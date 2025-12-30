package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.MyContextWrapper
import java.lang.ref.SoftReference

class V2RayVpnService : VpnService(), ServiceControl {
    private var mInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        V2RayServiceManager.serviceControl = SoftReference(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        V2RayServiceManager.startCoreLoop()
        return START_STICKY
    }

    override fun stopService() {
        stopV2Ray()
        stopSelf()
    }

    private fun stopV2Ray() {
        V2RayServiceManager.stopCoreLoop()
        // اصلاح اصلی برای حذف آیکون کلید VPN
        try {
            mInterface?.close()
            mInterface = null
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to close VPN interface", e)
        }
        NotificationManager.cancelNotification()
    }

    override fun onDestroy() {
        stopV2Ray()
        super.onDestroy()
    }

    override fun getService(): Service = this
    override fun startService() {}
    override fun vpnProtect(socket: Int): Boolean = protect(socket)

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let { MyContextWrapper.wrap(it, SettingsManager.getLocale()) }
        super.attachBaseContext(context)
    }

    override fun onRevoke() {
        stopService()
        super.onRevoke()
    }
}
