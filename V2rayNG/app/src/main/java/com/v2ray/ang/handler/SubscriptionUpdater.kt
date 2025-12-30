package com.v2ray.ang.handler

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R

object SubscriptionUpdater {
    class UpdateTask(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        @SuppressLint("MissingPermission")
        override suspend fun doWork(): Result {
            val subs = MmkvManager.decodeSubscriptions().filter { it.second.autoUpdate }
            if (subs.isEmpty()) return Result.success()

            val nm = NotificationManagerCompat.from(applicationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(AppConfig.SUBSCRIPTION_UPDATE_CHANNEL, AppConfig.SUBSCRIPTION_UPDATE_CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN))
            }

            val builder = NotificationCompat.Builder(applicationContext, AppConfig.SUBSCRIPTION_UPDATE_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(applicationContext.getString(R.string.title_pref_auto_update_subscription))
                .setPriority(NotificationCompat.PRIORITY_MIN)

            for (sub in subs) {
                builder.setContentText("Updating ${sub.second.remarks}")
                nm.notify(3, builder.build())
                AngConfigManager.updateConfigViaSub(Pair(sub.first, sub.second))
            }
            nm.cancel(3)
            return Result.success()
        }
    }
}
