package com.v2ray.ang.handler

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R

object SubscriptionUpdater {
    class UpdateTask(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        private val notificationManager = NotificationManagerCompat.from(applicationContext)

        @SuppressLint("MissingPermission")
        override suspend fun doWork(): Result {
            val subs = MmkvManager.decodeSubscriptions().filter { it.second.autoUpdate }
            if (subs.isEmpty()) return Result.success()

            val channelId = "subscription_update_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Subscription Update", NotificationManager.IMPORTANCE_MIN)
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(applicationContext.getString(R.string.title_pref_auto_update_subscription))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(false)

            for (sub in subs) {
                notification.setContentText("Updating ${sub.second.remarks}")
                notificationManager.notify(3, notification.build())
                AngConfigManager.updateConfigViaSub(Pair(sub.first, sub.second))
            }

            notificationManager.cancel(3)
            return Result.success()
        }
    }
}
