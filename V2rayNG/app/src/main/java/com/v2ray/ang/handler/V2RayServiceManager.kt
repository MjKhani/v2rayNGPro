package com.v2ray.ang.handler

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.ProfileItem
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
        if (MmkvManager.getSelectServer().isNullOrEmpty()) return false
        startContextService(context)
        return true
    }

    private fun startContextService(context: Context) {
        val intent = if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: AppConfig.VPN) == AppConfig.VPN) {
            Intent(context.applicationContext, V2RayVpnService::class.java)
        } else {
            Intent(context.applicationContext, V2RayProxyOnlyService::class.java)
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) context.startForegroundService(intent) else context.startService(intent)
    }

    fun startCoreLoop(): Boolean {
        if (coreController.isRunning) return false
        val service = serviceControl?.get()?.getService() ?: return false
        val guid = MmkvManager.getSelectServer() ?: return false
        val result = V2rayConfigManager.getV2rayConfig(service, guid)
        if (!result.status) return false
        
        currentConfig = MmkvManager.decodeServerConfig(guid)
        coreController.startLoop(result.content)
        
        NotificationManager.showNotification(currentConfig)
        return true
    }

    fun stopCoreLoop(): Boolean {
        if (coreController.isRunning) {
            CoroutineScope(Dispatchers.IO).launch { coreController.stopLoop() }
        }
        // حذف اعلان بلافاصله بعد از استاپ
        NotificationManager.cancelNotification()
        return true
    }

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
            if (intent?.getIntExtra("key", 0) == AppConfig.MSG_STATE_STOP) {
                serviceControl?.get()?.stopService()
            }
        }
    }
}
