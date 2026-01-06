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
import android.os.Handler
import android.os.Looper
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
    private var mInterface: ParcelFileDescriptor? = null
    
    @Volatile
    private var isRunning = false
    
    @Volatile
    private var isStopping = false
    
    private var tun2SocksService: Tun2SocksControl? = null

    /**destroy
     * Unfortunately registerDefaultNetworkCallback is going to return our VPN interface: https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val connectivity by lazy { getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager }

    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                // it's a good idea to refresh capabilities
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        V2RayServiceManager.serviceControl = SoftReference(this)
        Log.i(AppConfig.TAG, "V2RayVpnService onCreate")
    }

    override fun onRevoke() {
        Log.w(AppConfig.TAG, "VPN permission revoked by system")
        stopV2Ray()
    }

//    override fun onLowMemory() {
//        stopV2Ray()
//        super.onLowMemory()
//    }

    override fun onDestroy() {
        Log.i(AppConfig.TAG, "V2RayVpnService onDestroy START")
        
        // Force complete cleanup
        try {
            stopV2Ray(true)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Error during forced stop in onDestroy", e)
        }
        
        // Double check notification is cancelled
        try {
            NotificationManager.cancelNotification()
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Error canceling notification", e)
        }
        
        // Clear service reference
        V2RayServiceManager.serviceControl = null
        
        super.onDestroy()
        Log.i(AppConfig.TAG, "V2RayVpnService onDestroy COMPLETE")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (V2RayServiceManager.startCoreLoop()) {
            startService()
        }
        return START_STICKY
        //return super.onStartCommand(intent, flags, startId)
    }

    override fun getService(): Service {
        return this
    }

    override fun startService() {
        setupService()
    }

    override fun stopService() {
        stopV2Ray(true)
    }

    override fun vpnProtect(socket: Int): Boolean {
        return protect(socket)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }

    /**
     * Sets up the VPN service.
     * Prepares the VPN and configures it if preparation is successful.
     */
    private fun setupService() {
        val prepare = prepare(this)
        if (prepare != null) {
            return
        }

        if (configureVpnService() != true) {
            return
        }

        runTun2socks()
    }

    /**
     * Configures the VPN service.
     * @return True if the VPN service was configured successfully, false otherwise.
     */
    private fun configureVpnService(): Boolean {
        val builder = Builder()

        // Configure network settings (addresses, routing and DNS)
        configureNetworkSettings(builder)

        // Configure app-specific settings (session name and per-app proxy)
        configurePerAppProxy(builder)

        // Close the old interface since the parameters have been changed
        try {
            mInterface?.close()
        } catch (ignored: Exception) {
            // ignored
        }

        // Configure platform-specific features
        configurePlatformFeatures(builder)

        // Create a new interface using the builder and save the parameters
        try {
            mInterface = builder.establish()
            if (mInterface == null) {
                Log.e(AppConfig.TAG, "Failed to establish VPN interface - builder.establish() returned null")
                return false
            }
            isRunning = true
            return true
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to establish VPN interface", e)
            stopV2Ray()
        }
        return false
    }

    /**
     * Configures the basic network settings for the VPN.
     * This includes IP addresses, routing rules, and DNS servers.
     *
     * @param builder The VPN Builder to configure
     */
    private fun configureNetworkSettings(builder: Builder) {
        val vpnConfig = SettingsManager.getCurrentVpnInterfaceAddressConfig()
        val bypassLan = SettingsManager.routingRulesetsBypassLan()

        // Configure IPv4 settings
        builder.setMtu(SettingsManager.getVpnMtu())
        builder.addAddress(vpnConfig.ipv4Client, 30)

        // Configure routing rules
        if (bypassLan) {
            AppConfig.ROUTED_IP_LIST.forEach {
                val addr = it.split('/')
                builder.addRoute(addr[0], addr[1].toInt())
            }
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        // Configure IPv6 if enabled
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6) == true) {
            builder.addAddress(vpnConfig.ipv6Client, 126)
            if (bypassLan) {
                builder.addRoute("2000::", 3) // Currently only 1/8 of total IPv6 is in use
                builder.addRoute("fc00::", 18) // Xray-core default FakeIPv6 Pool
            } else {
                builder.addRoute("::", 0)
            }
        }

        // Configure DNS servers
        //if (MmkvManager.decodeSettingsBool(AppConfig.PREF_LOCAL_DNS_ENABLED) == true) {
        //  builder.addDnsServer(PRIVATE_VLAN4_ROUTER)
        //} else {
        SettingsManager.getVpnDnsServers().forEach {
            if (Utils.isPureIpAddress(it)) {
                builder.addDnsServer(it)
            }
        }

        builder.setSession(V2RayServiceManager.getRunningServerName())
    }

    /**
     * Configures platform-specific VPN features for different Android versions.
     *
     * @param builder The VPN Builder to configure
     */
    private fun configurePlatformFeatures(builder: Builder) {
        // Android P (API 28) and above: Configure network callbacks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to request default network", e)
            }
        }

        // Android Q (API 29) and above: Configure metering and HTTP proxy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APPEND_HTTP_PROXY)) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy(LOOPBACK, SettingsManager.getHttpPort()))
            }
        }
    }

    /**
     * Configures per-app proxy rules for the VPN builder.
     *
     * - If per-app proxy is not enabled, disallow the VPN service's own package.
     * - If no apps are selected, disallow the VPN service's own package.
     * - If bypass mode is enabled, disallow all selected apps (including self).
     * - If proxy mode is enabled, only allow the selected apps (excluding self).
     *
     * @param builder The VPN Builder to configure.
     */
    private fun configurePerAppProxy(builder: Builder) {
        val selfPackageName = BuildConfig.APPLICATION_ID

        // If per-app proxy is not enabled, disallow the VPN service's own package and return
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY) == false) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        // If no apps are selected, disallow the VPN service's own package and return
        val apps = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
        if (apps.isNullOrEmpty()) {
            builder.addDisallowedApplication(selfPackageName)
            return
        }

        val bypassApps = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS)
        // Handle the VPN service's own package according to the mode
        if (bypassApps) apps.add(selfPackageName) else apps.remove(selfPackageName)

        apps.forEach {
            try {
                if (bypassApps) {
                    // In bypass mode, disallow the selected apps
                    builder.addDisallowedApplication(it)
                } else {
                    // In proxy mode, only allow the selected apps
                    builder.addAllowedApplication(it)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(AppConfig.TAG, "Failed to configure app in VPN: ${e.localizedMessage}", e)
            }
        }
    }

    /**
     * Runs the tun2socks process.
     * Starts the tun2socks process with the appropriate parameters.
     */
    private fun runTun2socks() {
        val vpnInterface = mInterface
        if (vpnInterface == null) {
            Log.e(AppConfig.TAG, "Cannot start tun2socks: VPN interface is null")
            return
        }
        
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_USE_HEV_TUNNEL, true) == true) {
            tun2SocksService = TProxyService(
                context = applicationContext,
                vpnInterface = vpnInterface,
                isRunningProvider = { isRunning },
                restartCallback = { runTun2socks() }
            )
        } else {
            tun2SocksService = Tun2SocksService(
                context = applicationContext,
                vpnInterface = vpnInterface,
                isRunningProvider = { isRunning },
                restartCallback = { runTun2socks() }
            )
        }

        tun2SocksService?.startTun2Socks()
    }

    /**
     * Stops the V2Ray VPN service completely with proper cleanup order.
     */
    private fun stopV2Ray(isForced: Boolean = true) {
        // Prevent concurrent stop attempts
        synchronized(this) {
            if (isStopping) {
                Log.w(AppConfig.TAG, "Stop already in progress")
                return
            }
            
            if (!isRunning && mInterface == null) {
                Log.d(AppConfig.TAG, "Service already stopped")
                return
            }
            
            isStopping = true
        }

        Log.i(AppConfig.TAG, "=== Stopping V2Ray VPN Service (forced=$isForced) ===")

        try {
            // Step 1: Mark as not running to prevent new operations
            isRunning = false
            Log.d(AppConfig.TAG, "1. Marked as not running")

            // Step 2: Unregister network callback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    connectivity.unregisterNetworkCallback(defaultNetworkCallback)
                    Log.d(AppConfig.TAG, "2. Network callback unregistered")
                } catch (e: Exception) {
                    Log.d(AppConfig.TAG, "2. Network callback already unregistered")
                }
            }

            // Step 3: Stop tun2socks
            try {
                tun2SocksService?.stopTun2Socks()
                tun2SocksService = null
                Log.d(AppConfig.TAG, "3. Tun2socks stopped")
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "3. Failed to stop tun2socks", e)
            }

            // Step 4: Stop core loop (blocking)
            try {
                V2RayServiceManager.stopCoreLoop()
                Thread.sleep(150) // Give it time to complete
                Log.d(AppConfig.TAG, "4. Core loop stopped")
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "4. Failed to stop core loop", e)
            }

            // Step 5: Close VPN interface
            if (isForced) {
                try {
                    mInterface?.close()
                    mInterface = null
                    Log.d(AppConfig.TAG, "5. VPN interface closed")
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "5. Failed to close VPN interface", e)
                }

                // Step 6: Stop foreground and cancel notification
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    Log.d(AppConfig.TAG, "6. Foreground service stopped")
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "6. Failed to stop foreground", e)
                }

                // Step 7: Cancel notification with delay
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        NotificationManager.cancelNotification()
                        Log.d(AppConfig.TAG, "7. Notification cancelled (delayed)")
                    } catch (e: Exception) {
                        Log.e(AppConfig.TAG, "7. Failed to cancel notification", e)
                    }
                }, 200)

                // Step 8: Stop self service
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        stopSelf()
                        Log.d(AppConfig.TAG, "8. Service stopped via stopSelf()")
                    } catch (e: Exception) {
                        Log.e(AppConfig.TAG, "8. Failed to stopSelf", e)
                    }
                }, 250)
            }

            Log.i(AppConfig.TAG, "=== V2Ray VPN Service stop completed ===")

        } finally {
            isStopping = false
        }
    }
}
