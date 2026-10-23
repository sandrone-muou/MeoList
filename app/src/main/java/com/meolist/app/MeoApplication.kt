package com.meolist.app

import android.app.Application
import com.meolist.notification.NotificationStartup
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MeoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationStartup.onAppStart(this)
    }
}
