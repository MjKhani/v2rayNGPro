package com.v2ray.ang.handler

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
import com.v2ray.ang.util.Utils
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.lang.ref.SoftReference

object V2RayServiceManager {

    private val coreController: CoreController = Libv2ray.newCoreController(CoreCallback())
    private val mMsgReceive = ReceiveMessageHandler()
    private var currentConfig: ProfileItem? = null

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            Seq.setContext(value?.get()?.getService()?.applicationContext)
            Libv2ray.initCoreEnv(Utils.userAssetPath(value?.get()?.getService()), Utils.getDeviceIdForXUDPBaseKey())
        }

    fun startVServiceFromToggle(context: Context): Boolean {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            context.toast(R.string.app_tile_first_use)
            return false
        }
        startContextService(context)
        return true
    }

    fun startVService(context: Context, guid: String? = null) {
        if (guid != null) {
            MmkvManager.setSelectServer(guid)
        }
        startContextService(context)
    }

    fun stopVService(context: Context) {
        context.toast(R.string.toast_services_stop)
        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_STOP, "")
    }

    fun isRunning() = coreController.isRunning

    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    private fun startContextService(context: Context) {
        if (coreController.isRunning) return
        val guid = MmkvManager.getSelectServer() ?: return
        val config = MmkvManager.decodeServerConfig(guid) ?: return

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

    fun startCoreLoop(): Boolean {
        if (coreController.isRunning) return false
        val service = getService() ?: return false
        val guid = MmkvManager.getSelectServer() ?: return false
        val config = MmkvManager.decodeServerConfig(guid) ?: return false
        val result = V2rayConfigManager.getV2rayConfig(service, guid)
        if (!result.status) return false

        try {
            val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
            mFilter.addAction(Intent.ACTION_SCREEN_ON)
            mFilter.addAction(Intent.ACTION_SCREEN_OFF)
            ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())
        } catch (e: Exception) { return false }

        currentConfig = config
        try {
            coreController.startLoop(result.content)
        } catch (e: Exception) { return false }

        if (!coreController.isRunning) {
            NotificationManager.cancelNotification()
            return false
        }

        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
        NotificationManager.showNotification(currentConfig)
        NotificationManager.startSpeedNotification(currentConfig)
        PluginServiceManager.runPlugin(service, config, result.socksPort)
        return true
    }

    fun stopCoreLoop(): Boolean {
        val service = getService() ?: return false
        NotificationManager.stopSpeedNotification(currentConfig)

        if (coreController.isRunning) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coreController.stopLoop()
                } catch (e: Exception) {
                    Log.e(AppConfig.TAG, "Stop loop fail", e)
                }
            }
        }

        MessageUtil.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {}

        PluginServiceManager.stopPlugin()
        NotificationManager.cancelNotification()
        return true
    }

    private fun getService(): Service? = serviceControl?.get()?.getService()

    private class CoreCallback : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long {
            serviceControl?.get()?.stopService()
            return 0
        }
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }

    private class ReceiveMessageHandler : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_STOP -> serviceControl.stopService()
                AppConfig.MSG_STATE_RESTART -> {
                    serviceControl.stopService()
                    Thread.sleep(500L)
                    startVService(serviceControl.getService())
                }
            }
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> NotificationManager.stopSpeedNotification(currentConfig)
                Intent.ACTION_SCREEN_ON -> NotificationManager.startSpeedNotification(currentConfig)
            }
        }
    }
}
