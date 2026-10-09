package com.ggwebapp.servernotifications

import android.app.Application
import com.ggwebapp.servernotifications.push.Notifier

class ServerNotificationsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}
