package com.v2ray.ang.handler

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.dto.ProfileItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.service.ServiceControl
import com.v2ray.ang.service.V2RayProxyOnlyService
import com.v2ray.ang.service.V2RayVpnService
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.handler.PluginServiceManager
import com.v2ray.ang.util.Utils
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.lang.ref.SoftReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object V2RayServiceManager {

    private val coreController: CoreController = Libv2ray.newCoreController(CoreCallback())
    private val mMsgReceive = ReceiveMessageHandler()
    private var currentConfig: ProfileItem? = null
    
    @Volatile
    private var isStoppingCore = false

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            value?.get()?.getService()?.applicationContext?.let { context ->
                Seq.setContext(context)
                Libv2ray.initCoreEnv(Utils.userAssetPath(context), Utils.getDeviceIdForXUDPBaseKey())
            }
        }

    /**
     * Starts the V2Ray service from a toggle action.
     * @param context The context from which the service is started.
     * @return True if the service was started successfully, false otherwise.
     */
    fun startVServiceFromToggle(context: Context): Boolean {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        startContextService(context)
        return true
    }

    /**
     * Starts the V2Ray service.
     * @param context The context from which the service is started.
     * @param guid The GUID of the server configuration to use (optional).
     */
    fun startVService(context: Context, guid: String? = null) {
        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }
        startContextService(context)
    }

    /**
     * Stops the V2Ray service.
     * @param context The context from which the service is stopped.
     */
    fun stopVService(context: Context) {
        context.toast(R.string.toast_services_stop)
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    /**
     * Checks if the V2Ray service is running.
     * @return True if the service is running, false otherwise.
     */
    fun isRunning() = coreController.isRunning

    /**
     * Gets the name of the currently running server.
     * @return The name of the running server.
     */
    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    /**
     * Starts the context service for V2Ray.
     * Chooses between VPN service or Proxy-only service based on user settings.
     * @param context The context from which the service is started.
     */
    private fun startContextService(context: Context) {
        if (coreController.isRunning) {
            return
        }
        val guid = MmkvManager.getSelectServer() ?: return
        val config = MmkvManager.decodeServerConfig(guid) ?: return
        if (config.configType != EConfigType.CUSTOM
            && !Utils.isValidUrl(config.server)
            && !Utils.isPureIpAddress(config.server.orEmpty())
        ) return
//        val result = V2rayConfigUtil.getV2rayConfig(context, guid)
//        if (!result.status) return

        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING) == true) {
            context.toast(R.string.toast_warning_pref_proxysharing_short)
        } else {
            context.toast(R.string.toast_services_start)
        }
        val intent = if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: AppConfig.VPN) == AppConfig.VPN) {
            Intent(context.applicationContext, V2RayVpnService::class.java)
        } else {
            Intent(context.applicationContext, V2RayProxyOnlyService::class.java)
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     * Starts the V2Ray core service.
     */
    fun startCoreLoop(): Boolean {
        if (coreController.isRunning) {
            return false
        }

        val service = getService() ?: return false
        val guid = MmkvManager.getSelectServer() ?: return false
        val config = MmkvManager.decodeServerConfig(guid) ?: return false
        val result = V2rayConfigManager.getV2rayConfig(service, guid)
        if (!result.status)
            return false

        try {
            val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
            mFilter.addAction(Intent.ACTION_SCREEN_ON)
            mFilter.addAction(Intent.ACTION_SCREEN_OFF)
            mFilter.addAction(Intent.ACTION_USER_PRESENT)
            ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to register broadcast receiver", e)
            return false
        }

        currentConfig = config

        try {
            coreController.startLoop(result.content)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to start Core loop", e)
            return false
        }

        if (coreController.isRunning == false) {
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, "")
            NotificationManager.cancelNotification()
            return false
        }

        try {
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
            NotificationManager.showNotification(currentConfig)
            NotificationManager.startSpeedNotification(currentConfig)

            PluginServiceManager.runPlugin(service, config, result.socksPort)
        } catch (e: Exception) {
            Log.e(AppConfig.TAG, "Failed to startup service", e)
            return false
        }
        return true
    }

    /**
     * Stops the V2Ray core service completely and synchronously.
     * This ensures all resources are cleaned up before returning.
     */
    fun stopCoreLoop(): Boolean {
        // جلوگیری از multiple simultaneous stops
        synchronized(this) {
            if (isStoppingCore) {
                Log.w(AppConfig.TAG, "Core stop already in progress")
                return false
            }
            isStoppingCore = true
        }

        try {
            val service = getService()
            if (service == null) {
                Log.w(AppConfig.TAG, "Service is null, cannot stop core")
                return false
            }

            Log.i(AppConfig.TAG, "=== Starting complete core shutdown ===")

            // Step 1: Stop speed notification immediately
            try {
                NotificationManager.stopSpeedNotification(currentConfig)
                Log.d(AppConfig.TAG, "✓ Speed notification stopped")
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to stop speed notification", e)
            }

            // Step 2: Stop plugin
            try {
                PluginServiceManager.stopPlugin()
                Log.d(AppConfig.TAG, "✓ Plugin stopped")
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to stop plugin", e)
            }

            // Step 3: Stop core loop with timeout
            if (coreController.isRunning) {
                Log.i(AppConfig.TAG, "Stopping core controller...")
                val latch = CountDownLatch(1)
                
                Thread {
                    try {
                        coreController.stopLoop()
                        latch.countDown()
                    } catch (e: Exception) {
                        Log.e(AppConfig.TAG, "Exception stopping core", e)
                        latch.countDown()
                    }
                }.start()

                // Wait for core to stop (max 2 seconds)
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    Log.w(AppConfig.TAG, "Core stop timeout after 2 seconds")
                }

                // Verify core stopped
                var retries = 0
                while (coreController.isRunning && retries < 10) {
                    Thread.sleep(50)
                    retries++
                }

                if (coreController.isRunning) {
                    Log.e(AppConfig.TAG, "✗ Core still running after stop attempts!")
                } else {
                    Log.d(AppConfig.TAG, "✓ Core controller stopped")
                }
            }

            // Step 4: Unregister broadcast receiver
            try {
                service.unregisterReceiver(mMsgReceive)
                Log.d(AppConfig.TAG, "✓ Broadcast receiver unregistered")
            } catch (e: Exception) {
                Log.d(AppConfig.TAG, "Receiver already unregistered or not found")
            }

            // Step 5: Send stop success message
            try {
                MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
                Log.d(AppConfig.TAG, "✓ Stop success message sent")
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to send stop message", e)
            }

            // Step 6: Cancel notification (delayed to ensure UI update)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    NotificationManager.cancelNotification()
                    Log.d(AppConfig.TAG, "✓ Notification cancelled")
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Failed to cancel notification", e)
                }
            }, 100)

            Log.i(AppConfig.TAG, "=== Core shutdown completed ===")
            return true

        } finally {
            isStoppingCore = false
        }
    }

    /**
     * Queries the statistics for a given tag and link.
     * @param tag The tag to query.
     * @param link The link to query.
     * @return The statistics value.
     */
    fun queryStats(tag: String, link: String): Long {
        return coreController.queryStats(tag, link)
    }

    /**
     * Measures the connection delay for the current V2Ray configuration.
     * Tests with primary URL first, then falls back to alternative URL if needed.
     * Also fetches remote IP information if the delay test was successful.
     */
    private fun measureV2rayDelay() {
        if (coreController.isRunning == false) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val service = getService() ?: return@launch
            var time = -1L
            var errorStr = ""

            try {
                time = coreController.measureDelay(SettingsManager.getDelayTestUrl())
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to measure delay with primary URL", e)
                errorStr = e.message?.substringAfter("\":") ?: "empty message"
            }
            if (time == -1L) {
                try {
                    time = coreController.measureDelay(SettingsManager.getDelayTestUrl(true))
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Failed to measure delay with alternative URL", e)
                    errorStr = e.message?.substringAfter("\":") ?: "empty message"
                }
            }

            val result = if (time >= 0) {
                service.getString(R.string.connection_test_available, time)
            } else {
                service.getString(R.string.connection_test_error, errorStr)
            }
            MessageUtil.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_SUCCESS, result)

            // Only fetch IP info if the delay test was successful
            if (time >= 0) {
                SpeedtestManager.getRemoteIPInfo()?.let { ip ->
                    MessageUtil.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_SUCCESS, "$result\n$ip")
                }
            }
        }
    }

    /**
     * Gets the current service instance.
     * @return The current service instance, or null if not available.
     */
    private fun getService(): Service? {
        return serviceControl?.get()?.getService()
    }

    /**
     * Core callback handler implementation for handling V2Ray core events.
     * Handles startup, shutdown, socket protection, and status emission.
     */
    private class CoreCallback : CoreCallbackHandler {
        /**
         * Called when V2Ray core starts up.
         * @return 0 for success, any other value for failure.
         */
        override fun startup(): Long {
            return 0
        }

        /**
         * Called when V2Ray core shuts down.
         * @return 0 for success, any other value for failure.
         */
        override fun shutdown(): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                Log.e(AppConfig.TAG, "Failed to stop service in callback", e)
                -1
            }
        }

        /**
         * Called when V2Ray core emits status information.
         * @param l Status code.
         * @param s Status message.
         * @return Always returns 0.
         */
        override fun onEmitStatus(l: Long, s: String?): Long {
            return 0
        }
    }

    /**
     * Broadcast receiver for handling messages sent to the service.
     * Handles registration, service control, and screen events.
     */
    private class ReceiveMessageHandler : BroadcastReceiver() {
        /**
         * Handles received broadcast messages.
         * Processes service control messages and screen state changes.
         * @param ctx The context in which the receiver is running.
         * @param intent The intent being received.
         */
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    if (coreController.isRunning) {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageUtil.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
                    }
                }

                AppConfig.MSG_UNREGISTER_CLIENT -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_START -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_STOP -> {
                    Log.i(AppConfig.TAG, "Stop Service")
                    serviceControl.stopService()
                }

                AppConfig.MSG_STATE_RESTART -> {
                    Log.i(AppConfig.TAG, "Restart Service")
                    serviceControl.stopService()
                    Thread.sleep(500L)
                    startVService(serviceControl.getService())
                }

                AppConfig.MSG_MEASURE_DELAY -> {
                    measureV2rayDelay()
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(AppConfig.TAG, "SCREEN_OFF, stop querying stats")
                    NotificationManager.stopSpeedNotification(currentConfig)
                }

                Intent.ACTION_SCREEN_ON -> {
                    Log.i(AppConfig.TAG, "SCREEN_ON, start querying stats")
                    NotificationManager.startSpeedNotification(currentConfig)
                }
            }
        }
    }
}
