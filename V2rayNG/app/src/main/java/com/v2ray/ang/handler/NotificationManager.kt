package com.v2ray.ang.handler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.ui.MainActivity
import kotlinx.coroutines.*

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: NotificationManager? = null

    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentIntent = PendingIntent.getActivity(service, 0, Intent(service, MainActivity::class.java), flags)
        val stopIntent = PendingIntent.getBroadcast(service, 1, Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_STOP)
        }, flags)

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(AppConfig.RAY_NG_CHANNEL_ID, AppConfig.RAY_NG_CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN)
            getNotificationManager()?.createNotificationChannel(channel)
            AppConfig.RAY_NG_CHANNEL_ID
        } else ""

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(currentConfig?.remarks)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_delete_24dp, service.getString(R.string.notification_action_stop_v2ray), stopIntent)

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    fun cancelNotification() {
        speedNotificationJob?.cancel()
        val service = getService()
        if (service != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                service.stopForeground(true)
            }
        }
        getNotificationManager()?.cancel(NOTIFICATION_ID)
    }

    fun startSpeedNotification(currentConfig: ProfileItem?) {
        if (speedNotificationJob != null) return
        speedNotificationJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                // Speed logic placeholder
                delay(3000)
            }
        }
    }

    fun stopSpeedNotification(currentConfig: ProfileItem?) {
        speedNotificationJob?.cancel()
        speedNotificationJob = null
    }

    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    private fun getService(): Service? = V2RayServiceManager.serviceControl?.get()?.getService()
}
