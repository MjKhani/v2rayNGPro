package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

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

        val contentPendingIntent = PendingIntent.getActivity(service, 0, Intent(service, MainActivity::class.java), flags)

        val stopIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE).apply {
            `package` = AppConfig.ANG_PACKAGE
            putExtra("key", AppConfig.MSG_STATE_STOP)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(service, 1, stopIntent, flags)

        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel() else ""

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(currentConfig?.remarks)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_delete_24dp, service.getString(R.string.notification_action_stop_v2ray), stopPendingIntent)

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
        mBuilder = null
    }

    fun startSpeedNotification(currentConfig: ProfileItem?) {
        if (speedNotificationJob != null || !V2RayServiceManager.isRunning()) return
        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                // منطق آپدیت سرعت (بدون تغییر)
                delay(3000)
            }
        }
    }

    fun stopSpeedNotification(currentConfig: ProfileItem?) {
        speedNotificationJob?.cancel()
        speedNotificationJob = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val chan = NotificationChannel(AppConfig.RAY_NG_CHANNEL_ID, AppConfig.RAY_NG_CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN)
        getNotificationManager()?.createNotificationChannel(chan)
        return AppConfig.RAY_NG_CHANNEL_ID
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
