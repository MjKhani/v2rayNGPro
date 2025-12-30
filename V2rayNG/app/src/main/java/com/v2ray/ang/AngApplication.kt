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

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)
        SettingsManager.setNightMode()
        
        try {
            WorkManager.initialize(this, workManagerConfiguration)
        } catch (e: Exception) {}

        SettingsManager.initRoutingRulesets(this)

        es.dmoral.toasty.Toasty.Config.getInstance()
            .setGravity(android.view.Gravity.BOTTOM, 0, 200)
            .apply()

        setupAutoUpdateTask()
    }

    private fun setupAutoUpdateTask() {
        CoroutineScope(Dispatchers.Main).launch {
            delay(5000) 
            
            // استفاده از رشته مستقیم برای جلوگیری از هرگونه خطای Unresolved Reference
            val isAutoUpdateEnabled = MmkvManager.decodeSettingsBool("pref_sub_auto_update", true)
            if (isAutoUpdateEnabled) {
                val intervalStr = MmkvManager.decodeSettingsString("pref_sub_update_interval")
                val intervalMinutes = try {
                    intervalStr?.toLong() ?: 60L
                } catch (e: Exception) {
                    60L
                }
                
                if (intervalMinutes >= 15) {
                    try {
                        val updateRequest = androidx.work.PeriodicWorkRequest.Builder(
                            com.v2ray.ang.handler.SubscriptionUpdater.UpdateTask::class.java,
                            intervalMinutes,
                            java.util.concurrent.TimeUnit.MINUTES
                        )
                            .setInitialDelay(intervalMinutes, java.util.concurrent.TimeUnit.MINUTES)
                            .build()

                        WorkManager.getInstance(this@AngApplication).enqueueUniquePeriodicWork(
                            "subscription_update_task",
                            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                            updateRequest
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("v2rayNG", "AutoUpdate Task Error", e)
                    }
                }
            }
        }
    }
}
