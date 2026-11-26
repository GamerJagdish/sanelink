package com.sanelink.app.service

import android.os.SystemClock
import com.sanelink.app.SaneLinkApp
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sanelink.app.core.clipboard.ClipboardLogParser
import com.sanelink.app.core.clipboard.ClipboardLoopGuard
import com.sanelink.app.shizuku.ShizukuManager
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
 * 2. **Logcat denial line** (Android 10+) – every clipboard change makes the system log
 *    "Denying clipboard access to <our package>". Catches every copy in every app. Read either
 *    through Shizuku (shell privileges, no prompts) or our own logcat with `READ_LOGS` via ADB.
 * 3. **Accessibility copy heuristics** – "Copy"/"Copy link" clicks, "Copied" toasts and the
 *    Android 13+ clipboard overlay. Works without ADB but only for recognisable copy UIs.
 *
 * Signals 2 and 3 open [ClipboardReaderActivity] for a split second: Android 10+ only lets the
 * focused app read the clipboard.
 */
object ClipboardMonitor {

    private const val TAG = "SaneLinkClipboard"
    private const val LOGCAT_RESTART_DELAY_MS = 5_000L
    /** How long to let an installed Shizuku connect before falling back to our own log reader. */
    private const val SHIZUKU_GRACE_MS = 5_000L
    /** After the log-access prompt closes, how long to wait for proof of access before calling it declined. */
    private const val CONSENT_CHECK_MS = 4_000L
    private const val PROBE_LOOKBACK_MS = 5_000L

    data class Status(
        val serviceConnected: Boolean = false,
        /** `READ_LOGS` granted via ADB. */
        val logAccessGranted: Boolean = false,
        /** A logcat stream is currently running. */
        val logStreamRunning: Boolean = false,
        /** Shizuku's shell service is streaming clipboard signals (preferred over our own logcat). */
        val shizukuActive: Boolean = false,
        /**
         * Detection is proven to work: a system clipboard signal arrived (logcat or Shizuku), or
         * our log reader saw another process's lines, which Android only allows once access is granted.
         */
        val logStreamVerified: Boolean = false,
        /** Android 13+: the user declined the "access all device logs" prompt. */
        val logAccessDenied: Boolean = false,
        /** Android 13+: the stream must be (re)started while SaneLink is open. */
        val logStreamNeedsAppOpen: Boolean = false
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
    /** Mirrors the dashboard toggle, so paused protection never steals focus. */
    @Volatile private var autoSanitizeEnabled = true
    private var shizukuGraceUntil = 0L
    /** Android 13+: our log reader is running but access hasn't been confirmed yet. */
    @Volatile private var awaitingLogConsent = false
    private val consentCheck = Runnable { checkLogConsent() }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val ctx = appContext ?: return@OnPrimaryClipChangedListener
        // Only delivered when we are allowed to read (pre-Android 10, or SaneLink focused).
        // Skip our own writes (copy buttons, cleaned links) without reading them: a read
        // makes Android show "SaneLink pasted from your clipboard" for nothing.
        val cm = clipboardManager
        if (guard.isInEchoWindow() || (cm != null && ClipboardCleaner.holdsOwnClip(cm))) {
            return@OnPrimaryClipChangedListener
        }
        scope?.launch { ClipboardCleaner.cleanPrimaryClip(ctx) }
    }

    /** Called by [ClipboardAccessibilityService.onServiceConnected]. */
    fun start(context: Context) {
        val ctx = context.applicationContext
        if (appContext != null) stop()
        appContext = ctx
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            s.launch {
                SaneLinkApp.instance.preferences.autoSanitizeEnabled.collect { autoSanitizeEnabled = it }
            }
        }
        clipboardManager = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
            it.addPrimaryClipChangedListener(clipListener)
        }
        _status.update { it.copy(serviceConnected = true) }
        shizukuGraceUntil = SystemClock.uptimeMillis() + SHIZUKU_GRACE_MS
        refreshPermissionState(ctx)
        ShizukuManager.startWatching(
            onClipboardSignal = ::onSystemClipboardSignal,
            onActive = { active ->
                _status.update { it.copy(shizukuActive = active) }
                // Shizuku replaces our own logcat stream; fall back to it if Shizuku goes away.
                if (active) stopLogStream() else maybeStartLogStream()
            }
        )
        maybeStartLogStream()
    }

    /** Called when the accessibility service is unbound or destroyed. */
    fun stop() {
        ShizukuManager.stopWatching()
        // Drop pending focused reads and logcat restarts scheduled while the service ran
        mainHandler.removeCallbacksAndMessages(null)
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        clipboardManager = null
        stopLogStream()
        scope?.cancel()
        scope = null
        appContext = null
        _status.update { it.copy(serviceConnected = false, logStreamRunning = false, shizukuActive = false) }
    }

    /** SaneLink UI came to the foreground: good moment to (re)start the logcat stream. */
    fun onAppForeground(context: Context) {
        isAppInForeground = true
        refreshPermissionState(context.applicationContext)
        maybeStartLogStream()
        // Back from the log-access prompt (or opened while it's pending): check the answer shortly
        if (awaitingLogConsent) {
            mainHandler.removeCallbacks(consentCheck)
            mainHandler.postDelayed(consentCheck, CONSENT_CHECK_MS)
        }
    }

    /** "Ask again" after the log-access prompt was declined. Call while SaneLink is on screen. */
    fun retryLogAccess() {
        _status.update { it.copy(logAccessDenied = false) }
        stopLogStream()
        maybeStartLogStream()
    }

    /**
     * Android doesn't report the prompt's answer. If the stream has seen nothing from other
     * processes a few seconds after SaneLink is back on screen, the user declined: Android then
     * only shows the app its own logs. Stop the useless reader and say so on the setup page.
     */
    private fun checkLogConsent() {
        if (!awaitingLogConsent || !isAppInForeground) return
        if (logcatProcess?.isAliveCompat() != true) return
        awaitingLogConsent = false
        stopLogStream()
        _status.update { it.copy(logAccessDenied = true) }
    }

    private fun onLogAccessConfirmed() {
        awaitingLogConsent = false
        mainHandler.removeCallbacks(consentCheck)
        if (!_status.value.logStreamVerified || _status.value.logAccessDenied) {
            _status.update { it.copy(logStreamVerified = true, logAccessDenied = false) }
        }
    }

    fun onAppBackground() {
        isAppInForeground = false
    }

    fun refreshPermissionState(context: Context) {
        val granted = context.checkSelfPermission(Manifest.permission.READ_LOGS) ==
            PackageManager.PERMISSION_GRANTED
        _status.update { it.copy(logAccessGranted = granted) }
    }

    /** The system reported a clipboard change for our package (logcat or Shizuku). */
    private fun onSystemClipboardSignal() {
        onLogAccessConfirmed()
        requestFocusedRead()
    }


    /**
     * Something suggests the clipboard just changed. On Android 10+ we need focus to read it,
     * so briefly show the invisible reader.
     */
    fun requestFocusedRead(delayMillis: Long = 0L) {
        val ctx = appContext ?: return
        // Paused from the dashboard: don't open the reader (it would take focus for nothing)
        if (!autoSanitizeEnabled) return
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
        if (_status.value.shizukuActive) return
        if (!_status.value.logAccessGranted) return
        // Declined until the user taps "Ask again", so the prompt doesn't reappear on every open
        if (_status.value.logAccessDenied) return
        if (logcatProcess?.isAliveCompat() == true) return

        // Shizuku needs no log-access prompt. If it's installed, give it a moment to connect
        // after start-up instead of racing it and showing Android's prompt for nothing.
        val graceLeft = shizukuGraceUntil - SystemClock.uptimeMillis()
        if (graceLeft > 0 && ShizukuManager.state.value != ShizukuManager.State.NOT_INSTALLED) {
            mainHandler.postDelayed({ maybeStartLogStream() }, graceLeft)
            return
        }

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
        // Android 13+ shows a consent prompt for log access and doesn't report the answer:
        // stream a probe tag too, so seeing another process's line proves access was allowed.
        val needsConsent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val ownPid = android.os.Process.myPid()
        val process = try {
            // Start a few seconds back so SaneLink's own recent launch lines (logged by the system)
            // count as proof when Android already allowed access and shows no prompt.
            val since = System.currentTimeMillis() - if (needsConsent) PROBE_LOOKBACK_MS else 0L
            ProcessBuilder(ClipboardLogParser.buildLogcatCommand(since, includeProbe = needsConsent))
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start logcat", e)
            return
        }
        logcatProcess = process
        _status.update { it.copy(logStreamRunning = true, logStreamNeedsAppOpen = false) }
        awaitingLogConsent = needsConsent
        if (needsConsent) {
            mainHandler.removeCallbacks(consentCheck)
            mainHandler.postDelayed(consentCheck, CONSENT_CHECK_MS)
        }

        logcatThread = Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (awaitingLogConsent && ClipboardLogParser.isFromOtherProcess(line, ownPid)) {
                            mainHandler.post { onLogAccessConfirmed() }
                        }
                        if (ClipboardLogParser.isClipboardDenialFor(line, packageName)) {
                            mainHandler.post { onSystemClipboardSignal() }
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
        awaitingLogConsent = false
        mainHandler.removeCallbacks(consentCheck)
        val process = logcatProcess
        logcatProcess = null
        logcatThread = null
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        _status.update { it.copy(logStreamRunning = false) }
    }

    private fun Process.isAliveCompat(): Boolean = try {
        exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }
}
