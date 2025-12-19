package com.v2ray.ang

import android.content.Context
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AngApplication : MultiDexApplication() {
    companion object {
        lateinit var application: AngApplication
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)

        SettingsManager.setNightMode()
        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        SettingsManager.initRoutingRulesets(this)

        es.dmoral.toasty.Toasty.Config.getInstance()
            .setGravity(android.view.Gravity.BOTTOM, 0, 200)
            .apply()

        // تنظیم auto-update task در زمان راه‌اندازی برنامه
        setupAutoUpdateTaskOnStart()
    }

    private fun setupAutoUpdateTaskOnStart() {
        CoroutineScope(Dispatchers.IO).launch {
            // تاخیر برای اطمینان از کامل شدن راه‌اندازی MMKV
            delay(1000)
            
            // بررسی آیا auto-update فعال است
            val autoUpdateEnabled = MmkvManager.decodeSettingsBool(
                AppConfig.SUBSCRIPTION_AUTO_UPDATE, 
                true  // پیش‌فرض true
            )
            
            if (autoUpdateEnabled) {
                val interval = MmkvManager.decodeSettingsString(
                    AppConfig.SUBSCRIPTION_AUTO_UPDATE_INTERVAL,
                    AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL
                )
                val interval = intervalStr?.toLongOrNull() ?: AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL.toLong()
                
                // فقط اگر interval معتبر است task را تنظیم کن
                if (interval >= 15) {
                    // استفاده از RemoteWorkManager برای تنظیم task
                    val rw = androidx.work.multiprocess.RemoteWorkManager.getInstance(this@AngApplication)
                    rw.enqueueUniquePeriodicWork(
                        AppConfig.SUBSCRIPTION_UPDATE_TASK_NAME,
                        androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                        androidx.work.PeriodicWorkRequest.Builder(
                            com.v2ray.ang.handler.SubscriptionUpdater.UpdateTask::class.java,
                            interval,
                            java.util.concurrent.TimeUnit.MINUTES
                        )
                            .apply {
                                setInitialDelay(interval, java.util.concurrent.TimeUnit.MINUTES)
                            }
                            .build()
                    )
                    
                    android.util.Log.i(AppConfig.TAG, "Auto-update task configured on app start with interval: $interval minutes")
                }
            }
        }
    }
}
