package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.MyContextWrapper
import java.lang.ref.SoftReference

class V2RayProxyOnlyService : Service(), ServiceControl {
    override fun onCreate() {
        super.onCreate()
        V2RayServiceManager.serviceControl = SoftReference(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        V2RayServiceManager.startCoreLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        V2RayServiceManager.stopCoreLoop()
        NotificationManager.cancelNotification()
        super.onDestroy()
    }

    override fun getService(): Service = this
    override fun startService() {}
    override fun stopService() = stopSelf()
    override fun vpnProtect(socket: Int): Boolean = true
    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.N)
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let { MyContextWrapper.wrap(it, SettingsManager.getLocale()) }
        super.attachBaseContext(context)
    }
}
