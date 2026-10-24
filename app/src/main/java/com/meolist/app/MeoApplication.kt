package com.meolist.app

import android.app.Application
import android.content.Context
import com.meolist.core.locale.LanguageStore
import com.meolist.notification.NotificationStartup
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MeoApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageStore.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        NotificationStartup.onAppStart(this)
    }
}
