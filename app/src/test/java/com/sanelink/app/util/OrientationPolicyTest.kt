package com.sanelink.app.util

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationPolicyTest {

    @Test
    fun `phones lock to user portrait when full screen`() {
        for (widthDp in listOf(320, 360, 412, 599)) {
            assertTrue(OrientationPolicy.shouldLockPortrait(widthDp, isInMultiWindowMode = false))
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
                OrientationPolicy.requestedOrientationFor(widthDp, isInMultiWindowMode = false)
            )
        }
    }

    @Test
    fun `tablets allow free rotation in portrait and landscape`() {
        for (widthDp in listOf(600, 720, 800, 1024)) {
            assertFalse(OrientationPolicy.shouldLockPortrait(widthDp, isInMultiWindowMode = false))
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                OrientationPolicy.requestedOrientationFor(widthDp, isInMultiWindowMode = false)
            )
        }
    }

    @Test
    fun `multi-window sessions never force portrait`() {
        for (widthDp in listOf(360, 599, 600, 800)) {
            assertFalse(OrientationPolicy.shouldLockPortrait(widthDp, isInMultiWindowMode = true))
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                OrientationPolicy.requestedOrientationFor(widthDp, isInMultiWindowMode = true)
            )
        }
    }

    @Test
    fun `undefined or non-positive smallest width does not lock orientation`() {
        for (widthDp in listOf(-1, 0)) {
            assertFalse(OrientationPolicy.shouldLockPortrait(widthDp, isInMultiWindowMode = false))
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                OrientationPolicy.requestedOrientationFor(widthDp, isInMultiWindowMode = false)
            )
        }
    }
}
