package com.sanelink.app.util

import android.content.pm.ActivityInfo
import android.content.res.Configuration

/**
 * Keeps [com.sanelink.app.MainActivity] in portrait on phones while allowing tablets and
 * multi-window sessions to rotate freely.
 *
 * Uses [ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT] so phones that support 180° two-way
 * portrait can flip vertically when auto-rotate is enabled without ever entering landscape.
 */
object OrientationPolicy {
    const val TABLET_SMALLEST_WIDTH_DP = 600

    /** Returns whether the screen should be locked to portrait for the given window geometry. */
    fun shouldLockPortrait(
        smallestScreenWidthDp: Int,
        isInMultiWindowMode: Boolean
    ): Boolean = !isInMultiWindowMode &&
        smallestScreenWidthDp != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED &&
        smallestScreenWidthDp in 1 until TABLET_SMALLEST_WIDTH_DP

    /** Returns the [ActivityInfo] orientation constant to apply to the main UI activity. */
    fun requestedOrientationFor(
        smallestScreenWidthDp: Int,
        isInMultiWindowMode: Boolean
    ): Int = if (shouldLockPortrait(smallestScreenWidthDp, isInMultiWindowMode)) {
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
