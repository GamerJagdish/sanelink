package com.sanelink.app.shizuku;

/** Called from the Shizuku shell process whenever the system reports a clipboard change. */
interface IClipboardSignalCallback {
    oneway void onClipboardChanged();

    /** The shell-side log reader ended without being asked to stop. */
    oneway void onWatchStopped();
}
