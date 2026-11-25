package com.sanelink.app.service

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sanelink.app.core.clipboard.ClipboardLogParser
import com.sanelink.app.core.clipboard.ClipboardLoopGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Background clipboard watcher, hosted by [ClipboardAccessibilityService] (whose binding
 * keeps our process alive and exempts us from Android's background-activity-start limits).
 *
 * Change signals, in order of reliability:
 * 1. **Clipboard listener** – delivered directly on Android 8–9 (and whenever SaneLink is
 *    focused). We clean in-process, no UI needed.
 * 2. **Logcat denial line** (Android 10+, needs `READ_LOGS` via ADB) – every clipboard change
 *    makes the system log "Denying clipboard access to <our package>". Catches every copy in
 *    every app.
 * 3. **Accessibility copy heuristics** – "Copy"/"Copy link" clicks, "Copied" toasts and the
 *    Android 13+ clipboard overlay. Works without ADB but only for recognisable copy UIs.
 *
 * Signals 2 and 3 open [ClipboardReaderActivity] for a split second: Android 10+ only lets the
 * focused app read the clipboard.
 */
object ClipboardMonitor {

    private const val TAG = "SaneLinkClipboard"
    private const val LOGCAT_RESTART_DELAY_MS = 5_000L

    data class Status(
        val serviceConnected: Boolean = false,
        /** `READ_LOGS` granted via ADB. */
        val logAccessGranted: Boolean = false,
        /** A logcat stream is currently running. */
        val logStreamRunning: Boolean = false,
        /** At least one denial line was received, proving the stream sees system logs. */
        val logStreamVerified: Boolean = false,
        /** Android 13+: the stream must be (re)started while SaneLink is open. */
        val logStreamNeedsAppOpen: Boolean = false,
        val cleanedThisSession: Int = 0
    )

    val guard = ClipboardLoopGuard()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var clipboardManager: ClipboardManager? = null
    private var logcatProcess: Process? = null
    private var logcatThread: Thread? = null
    @Volatile private var isAppInForeground = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val ctx = appContext ?: return@OnPrimaryClipChangedListener
        // Only delivered when we are allowed to read (pre-Android 10, or SaneLink focused).
        scope?.launch { ClipboardCleaner.cleanPrimaryClip(ctx) }
    }

    /** Called by [ClipboardAccessibilityService.onServiceConnected]. */
    fun start(context: Context) {
        val ctx = context.applicationContext
        if (appContext != null) stop()
        appContext = ctx
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        clipboardManager = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
            it.addPrimaryClipChangedListener(clipListener)
        }
        _status.update { it.copy(serviceConnected = true) }
        refreshPermissionState(ctx)
        maybeStartLogStream()
    }

    /** Called when the accessibility service is unbound or destroyed. */
    fun stop() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        clipboardManager = null
        stopLogStream()
        scope?.cancel()
        scope = null
        appContext = null
        _status.update { it.copy(serviceConnected = false, logStreamRunning = false) }
    }

    /** SaneLink UI came to the foreground: good moment to (re)start the logcat stream. */
    fun onAppForeground(context: Context) {
        isAppInForeground = true
        refreshPermissionState(context.applicationContext)
        maybeStartLogStream()
    }

    fun onAppBackground() {
        isAppInForeground = false
    }

    fun refreshPermissionState(context: Context) {
        val granted = context.checkSelfPermission(Manifest.permission.READ_LOGS) ==
            PackageManager.PERMISSION_GRANTED
        _status.update { it.copy(logAccessGranted = granted) }
    }

    internal fun reportCleaned() {
        _status.update { it.copy(cleanedThisSession = it.cleanedThisSession + 1) }
    }

    /**
     * Something suggests the clipboard just changed. On Android 10+ we need focus to read it,
     * so briefly show the invisible reader.
     */
    fun requestFocusedRead(delayMillis: Long = 0L) {
        val ctx = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Background reads are allowed; the listener normally already handled it.
            scope?.launch { ClipboardCleaner.cleanPrimaryClip(ctx) }
            return
        }
        if (isAppInForeground) {
            // We already have focus; the listener receives the change directly.
            return
        }
        mainHandler.postDelayed({
            if (!guard.tryAcquireFocusedRead()) return@postDelayed
            try {
                ctx.startActivity(ClipboardReaderActivity.createIntent(ctx))
            } catch (e: Exception) {
                Log.w(TAG, "Unable to open clipboard reader", e)
            }
        }, delayMillis)
    }

    // ---- Logcat stream (Android 10+) -------------------------------------------------------

    private fun maybeStartLogStream() {
        val ctx = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return // not needed
        if (!_status.value.logAccessGranted) return
        if (logcatProcess?.isAliveCompat() == true) return

        // Android 13+ asks the user to approve log access, and auto-declines requests made
        // while the app is in the background. Only start when SaneLink is on screen.
        val needsForeground = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (needsForeground && !isAppInForeground) {
            _status.update { it.copy(logStreamNeedsAppOpen = true) }
            return
        }
        startLogStream(ctx.packageName)
    }

    private fun startLogStream(packageName: String) {
        val process = try {
            ProcessBuilder(ClipboardLogParser.buildLogcatCommand(System.currentTimeMillis()))
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start logcat", e)
            return
        }
        logcatProcess = process
        _status.update { it.copy(logStreamRunning = true, logStreamNeedsAppOpen = false) }

        logcatThread = Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (ClipboardLogParser.isClipboardDenialFor(line, packageName)) {
                            if (!_status.value.logStreamVerified) {
                                _status.update { it.copy(logStreamVerified = true) }
                            }
                            mainHandler.post { requestFocusedRead() }
                        }
                    }
                }
            } catch (_: Exception) {
                // Stream closed
            } finally {
                onLogStreamEnded(process)
            }
        }, "SaneLink-logcat").apply {
            isDaemon = true
            start()
        }
    }

    private fun onLogStreamEnded(process: Process) {
        mainHandler.post {
            if (logcatProcess !== process) return@post
            logcatProcess = null
            logcatThread = null
            _status.update { it.copy(logStreamRunning = false) }
            // Unexpected exit while the service still runs: retry later.
            if (appContext != null) {
                mainHandler.postDelayed({ maybeStartLogStream() }, LOGCAT_RESTART_DELAY_MS)
            }
        }
    }

    private fun stopLogStream() {
        val process = logcatProcess
        logcatProcess = null
        logcatThread = null
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
    }

    private fun Process.isAliveCompat(): Boolean = try {
        exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }
}
