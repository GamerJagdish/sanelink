package com.sanelink.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.sanelink.app.data.preferences.AppPreferences
import com.sanelink.app.data.repository.RuleRepository
import com.sanelink.app.service.ClipboardMonitor
import com.sanelink.app.shizuku.ShizukuManager

class SaneLinkApp : Application() {

    lateinit var preferences: AppPreferences
        private set

    lateinit var repository: RuleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = AppPreferences(this)
        repository = RuleRepository(this, preferences)
        registerActivityLifecycleCallbacks(ForegroundTracker)
        ShizukuManager.init(this)
    }

    /**
     * Tells [ClipboardMonitor] when the main UI is on screen. Android 13+ only grants log
     * access (with a one-time prompt) to apps in the foreground, so the logcat stream is
     * (re)started from here.
     */
    private object ForegroundTracker : ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (activity is MainActivity) ClipboardMonitor.onAppForeground(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (activity is MainActivity) ClipboardMonitor.onAppBackground()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    companion object {
        lateinit var instance: SaneLinkApp
            private set
    }
}
