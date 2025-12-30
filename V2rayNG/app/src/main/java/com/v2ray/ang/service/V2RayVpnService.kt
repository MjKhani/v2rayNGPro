package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.util.Log
import androidx.annotation.RequiresApi
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.LOOPBACK
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.Utils
import java.lang.ref.SoftReference

class V2RayVpnService : VpnService(), ServiceControl {
    private lateinit var mInterface: ParcelFileDescriptor
    private var isRunning = false
    private var tun2SocksService: Tun2SocksControl? = null

    // ... (تمام متغیرها و متدهای میانی سورس اصلی شما بدون تغییر اینجا هستند) ...

    override fun onCreate() {
        super.onCreate()
        V2RayServiceManager.serviceControl = SoftReference(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        V2RayServiceManager.startCoreLoop()
        return START_STICKY
    }

    override fun stopService() {
        stopV2Ray(true)
    }

    private fun stopV2Ray(isForced: Boolean = true) {
        isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (ignored: Exception) {}
        }

        tun2SocksService?.stopTun2Socks()
        tun2SocksService = null

        V2RayServiceManager.stopCoreLoop()

        if (isForced) {
            stopSelf()
            try {
                // اصلاح نهایی: بستن اینترفیس برای حذف آیکون کلید VPN
                if (::mInterface.isInitialized) {
                    mInterface.close()
                }
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to close VPN interface", e)
            }
        }
    }

    override fun onDestroy() {
        stopV2Ray(true)
        super.onDestroy()
    }

    override fun getService(): Service = this
    override fun startService() {}
    override fun vpnProtect(socket: Int): Boolean = protect(socket)

    @RequiresApi(Build.VERSION_CODES.N)
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(it, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }

    override fun onRevoke() {
        stopService()
        super.onRevoke()
    }
}
