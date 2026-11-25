package com.sanelink.app.shizuku;

import com.sanelink.app.shizuku.IClipboardSignalCallback;

/** Runs inside a Shizuku user service process with shell (or root) privileges. */
interface IShellService {
    /** Reserved by Shizuku: called when the service is unbound with remove = true. */
    void destroy() = 16777114;

    /** Runs `pm grant <packageName> <permission>`; returns the exit code. */
    int grantPermission(String packageName, String permission) = 1;

    /** Streams ClipboardService logs and reports denials for packageName to callback. */
    boolean startClipboardWatch(String packageName, IClipboardSignalCallback callback) = 2;

    void stopClipboardWatch() = 3;

    boolean isWatching() = 4;
}
