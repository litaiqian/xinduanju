package top.ipla.drama

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DramaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "proxy_service",
                "代理服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "代理IP后台服务"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
