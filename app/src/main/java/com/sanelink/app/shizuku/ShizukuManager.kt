package com.sanelink.app.shizuku

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.sanelink.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * App-side bridge to Shizuku.
 *
 * When Shizuku is running and SaneLink has its permission, we bind [ShellService] (shell
 * privileges) which streams clipboard-change signals back to us. Compared with the plain ADB
 * `READ_LOGS` grant this survives app restarts without Android 13+'s log-access prompt. As a
 * bonus the shell service also grants `READ_LOGS` so the ADB path keeps working if Shizuku
 * stops later.
 */
object ShizukuManager {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val TAG = "SaneLinkShizuku"
    private const val PERMISSION_REQUEST_CODE = 4127
    private const val MIN_SHIZUKU_VERSION = 11
    private const val WATCH_RESTART_DELAY_MS = 3_000L

    enum class State {
        /** Shizuku app isn't installed. */
        NOT_INSTALLED,

        /** Installed but its service isn't started (e.g. after a reboot). */
        NOT_RUNNING,

        /** Running, SaneLink hasn't been allowed yet. */
        PERMISSION_REQUIRED,

        /** Allowed; waiting for the background service to need it or to connect. */
        READY,

        /** Shell service is streaming clipboard signals. */
        ACTIVE,

        /** Shizuku version too old or the service failed. */
        UNAVAILABLE
    }

    private val _state = MutableStateFlow(State.NOT_INSTALLED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var wantWatching = false
    private var shellService: IShellService? = null
    private var onSignal: (() -> Unit)? = null
    private var onActiveChanged: ((Boolean) -> Unit)? = null

    private val serviceArgs by lazy {
        val ctx = requireNotNull(appContext)
        Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shizuku")
            .tag("sanelink-shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    /** Binder calls into the shell process can block (pm grant spawns a process); keep them off the main thread. */
    private val ipcExecutor = Executors.newSingleThreadExecutor { Thread(it, "SaneLink-shizuku-ipc") }

    private val signalCallback = object : IClipboardSignalCallback.Stub() {
        override fun onClipboardChanged() {
            mainHandler.post { onSignal?.invoke() }
        }

        override fun onWatchStopped() {
            // The shell log reader died: report it and try to start it again shortly.
            mainHandler.post {
                if (shellService == null || !wantWatching) return@post
                setActive(false, State.READY)
                mainHandler.postDelayed({ startWatchInBackground() }, WATCH_RESTART_DELAY_MS)
            }
        }
    }

    private fun startWatchInBackground() {
        val service = shellService ?: return
        val ctx = appContext ?: return
        ipcExecutor.execute {
            val started = try {
                wantWatching && service.startClipboardWatch(ctx.packageName, signalCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start Shizuku clipboard watch", e)
                false
            }
            mainHandler.post {
                if (shellService !== service) return@post
                setActive(started, if (started) State.ACTIVE else State.READY)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = binder?.takeIf { it.pingBinder() }?.let { IShellService.Stub.asInterface(it) }
            if (service == null) {
                setActive(false, State.UNAVAILABLE)
                return
            }
            shellService = service
            val ctx = appContext ?: return
            ipcExecutor.execute {
                try {
                    // Keep the ADB fallback alive in case Shizuku stops later.
                    if (ctx.checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) {
                        service.grantPermission(ctx.packageName, Manifest.permission.READ_LOGS)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to grant READ_LOGS through Shizuku", e)
                }
                mainHandler.post { startWatchInBackground() }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            setActive(false, computeState())
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refresh()
        if (wantWatching) bindIfPossible()
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        // Shizuku's server died, but our shell helper is its own process and would keep its log
        // reader running. Its binder is still reachable directly, so ask it to exit. This runs
        // once, only when Shizuku goes away: no polling, nothing extra in the background.
        shellService?.let { orphan ->
            ipcExecutor.execute {
                try {
                    orphan.destroy()
                } catch (_: Exception) {
                    // Already gone, or exited while answering
                }
            }
        }
        shellService = null
        setActive(false, computeState())
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, _ ->
        if (code != PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
        refresh()
        if (wantWatching) bindIfPossible()
    }

    /** Called once from [com.sanelink.app.SaneLinkApp.onCreate]. */
    fun init(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(binderReceived, mainHandler)
        Shizuku.addBinderDeadListener(binderDead, mainHandler)
        Shizuku.addRequestPermissionResultListener(permissionResult, mainHandler)
        refresh()
    }

    /**
     * Starts streaming clipboard signals through Shizuku whenever it is available.
     * [onClipboardSignal] runs on the main thread; [onActive] reports whether Shizuku is
     * currently the active signal source.
     */
    fun startWatching(onClipboardSignal: () -> Unit, onActive: (Boolean) -> Unit) {
        wantWatching = true
        onSignal = onClipboardSignal
        onActiveChanged = onActive
        bindIfPossible()
    }

    fun stopWatching() {
        wantWatching = false
        try {
            shellService?.stopClipboardWatch()
        } catch (_: Exception) {
        }
        if (Shizuku.pingBinder()) {
            try {
                Shizuku.unbindUserService(serviceArgs, connection, true)
            } catch (_: Exception) {
            }
        }
        shellService = null
        setActive(false, computeState())
        onSignal = null
        onActiveChanged = null
    }

    /** Re-evaluates install / running / permission state (e.g. when the settings page resumes). */
    fun refresh() {
        val current = computeState()
        if (_state.value == State.ACTIVE && shellService != null) {
            if (current == State.READY) return // still allowed and connected
            // Access was revoked (or Shizuku stopped) while we were connected: drop the link.
            try {
                shellService?.stopClipboardWatch()
            } catch (_: Exception) {
            }
            shellService = null
            setActive(false, current)
            return
        }
        _state.value = current
    }

    /** Shows Shizuku's permission dialog. Call from the UI. */
    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            refresh()
            return
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refresh()
                if (wantWatching) bindIfPossible()
            } else {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to request Shizuku permission", e)
        }
    }

    /** Intent that opens the Shizuku app, or null if it isn't installed. */
    fun launchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun bindIfPossible() {
        if (computeState() != State.READY) {
            refresh()
            return
        }
        if (shellService != null) return
        try {
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to bind Shizuku user service", e)
            _state.value = State.UNAVAILABLE
        }
    }

    private fun setActive(active: Boolean, newState: State) {
        _state.value = newState
        onActiveChanged?.invoke(active)
    }

    private fun computeState(): State {
        val ctx = appContext ?: return State.NOT_INSTALLED
        if (!isInstalled(ctx)) return State.NOT_INSTALLED
        if (!Shizuku.pingBinder()) return State.NOT_RUNNING
        return try {
            when {
                Shizuku.isPreV11() || Shizuku.getVersion() < MIN_SHIZUKU_VERSION -> State.UNAVAILABLE
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.PERMISSION_REQUIRED
                else -> State.READY
            }
        } catch (_: Exception) {
            State.NOT_RUNNING
        }
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
