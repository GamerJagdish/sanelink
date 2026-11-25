package com.sanelink.app.shizuku

import android.os.IBinder
import android.os.RemoteException
import com.sanelink.app.core.clipboard.ClipboardLogParser
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * Shizuku user service. Shizuku starts this class in its own process running as the shell
 * user (uid 2000) or root, using SaneLink's APK as the classpath.
 *
 * Shell can read every app's log lines without `READ_LOGS` consent prompts, so this process
 * streams `ClipboardService` logs and pings SaneLink whenever the system refuses to notify
 * SaneLink of a clipboard change, i.e. whenever anything is copied. It never reads the
 * clipboard itself and only talks to SaneLink over a local binder.
 *
 * No Android Context is available here: keep this class dependency-light.
 */
class ShellService : IShellService.Stub() {

    private val lock = Any()
    private var logcat: Process? = null
    private var callback: IClipboardSignalCallback? = null
    private var deathRecipient: IBinder.DeathRecipient? = null

    override fun destroy() {
        stopClipboardWatch()
        exitProcess(0)
    }

    override fun grantPermission(packageName: String, permission: String): Int = try {
        ProcessBuilder("pm", "grant", packageName, permission)
            .redirectErrorStream(true)
            .start()
            .also { it.inputStream.readBytes() }
            .waitFor()
    } catch (_: Exception) {
        -1
    }

    override fun startClipboardWatch(packageName: String, cb: IClipboardSignalCallback): Boolean {
        stopClipboardWatch()
        val process = try {
            ProcessBuilder(ClipboardLogParser.buildLogcatCommand(System.currentTimeMillis()))
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            return false
        }

        // If SaneLink dies, stop streaming.
        val recipient = IBinder.DeathRecipient { stopClipboardWatch() }
        try {
            cb.asBinder().linkToDeath(recipient, 0)
        } catch (_: RemoteException) {
            process.destroy()
            return false
        }

        synchronized(lock) {
            logcat = process
            callback = cb
            deathRecipient = recipient
        }

        Thread({
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (ClipboardLogParser.isClipboardDenialFor(line, packageName)) {
                            try {
                                cb.onClipboardChanged()
                            } catch (_: RemoteException) {
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Stream closed
            } finally {
                synchronized(lock) {
                    if (logcat === process) {
                        logcat = null
                    }
                }
            }
        }, "SaneLink-shizuku-logcat").apply {
            isDaemon = true
            start()
        }
        return true
    }

    override fun stopClipboardWatch() {
        val process: Process?
        val cb: IClipboardSignalCallback?
        val recipient: IBinder.DeathRecipient?
        synchronized(lock) {
            process = logcat
            cb = callback
            recipient = deathRecipient
            logcat = null
            callback = null
            deathRecipient = null
        }
        if (cb != null && recipient != null) {
            try {
                cb.asBinder().unlinkToDeath(recipient, 0)
            } catch (_: Exception) {
            }
        }
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
    }

    override fun isWatching(): Boolean = synchronized(lock) { logcat != null }
}
