package com.v2ray.ang.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.util.MyContextWrapper
import java.lang.ref.SoftReference

class V2RayProxyOnlyService : Service(), ServiceControl {
    /**
     * Initializes the service.
     */
    override fun onCreate() {
        super.onCreate()
        V2RayServiceManager.serviceControl = SoftReference(this)
        Log.i(AppConfig.TAG, "V2RayProxyOnlyService created")
    }

    /**
     * Handles the start command for the service.
     * @param intent The intent.
     * @param flags The flags.
     * @param startId The start ID.
     * @return The start mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(AppConfig.TAG, "V2RayProxyOnlyService onStartCommand")
        V2RayServiceManager.startCoreLoop()
        return START_STICKY
    }

    /**
     * Destroys the service.
     */
    override fun onDestroy() {
        Log.i(AppConfig.TAG, "V2RayProxyOnlyService destroying")
        // اطمینان از توقف کامل قبل از destroy
        try {
            V2RayServiceManager.stopCoreLoop()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to stop core loop in onDestroy", e)
        }
        V2RayServiceManager.serviceControl = null // پاکسازی reference
        super.onDestroy()
        Log.i(AppConfig.TAG, "V2RayProxyOnlyService destroyed")
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    override fun getService(): Service {
        return this
    }

    /**
     * Starts the service.
     */
    override fun startService() {
        // do nothing
    }

    /**
     * Stops the service.
     */
    override fun stopService() {
        Log.i(AppConfig.TAG, "Stopping V2RayProxyOnlyService")
        try {
            stopSelf()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to stop service", e)
        }
    }

    /**
     * Protects the VPN socket.
     * @param socket The socket to protect.
     * @return True if the socket is protected, false otherwise.
     */
    override fun vpnProtect(socket: Int): Boolean {
        return true
    }

    /**
     * Binds the service.
     * @param intent The intent.
     * @return The binder.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Attaches the base context to the service.
     * @param newBase The new base context.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }
}
