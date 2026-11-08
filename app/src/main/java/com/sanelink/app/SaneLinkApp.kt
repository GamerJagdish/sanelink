package com.sanelink.app

import android.app.Application
import com.sanelink.app.data.preferences.AppPreferences
import com.sanelink.app.data.repository.RuleRepository

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
    }

    companion object {
        lateinit var instance: SaneLinkApp
            private set
    }
}
