package com.xrayrn.core

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.facebook.react.bridge.*
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.BaseActivityEventListener
import com.xrayrn.vpn.XrayVpnService
import libv2ray.CoreController
import libv2ray.CoreCallbackHandler

class V2RayCoreModule(private val rc: ReactApplicationContext) :
    ReactContextBaseJavaModule(rc) {

    private var controller: CoreController? = null

    private val VPN_REQ_CODE = 10086
    private var pendingVpnPromise: Promise? = null

    private val activityEventListener: ActivityEventListener =
        object : BaseActivityEventListener() {
            override fun onActivityResult(
                activity: Activity,
                requestCode: Int,
                resultCode: Int,
                data: Intent?
            ) {
                if (requestCode != VPN_REQ_CODE) return

                val p = pendingVpnPromise
                pendingVpnPromise = null

                if (resultCode == Activity.RESULT_OK) {
                    startVpnService()
                    p?.resolve("STARTED")
                } else {
                    p?.resolve("DENIED")
                }
            }
        }

    init {
        rc.addActivityEventListener(activityEventListener)
    }

    override fun getName(): String = "V2RayCore"

    private fun ensureController() {
        if (controller == null) {
            controller = CoreController(object : CoreCallbackHandler {
                override fun startup(): Long = 0L
                override fun shutdown(): Long = 0L
                override fun onEmitStatus(code: Long, msg: String?): Long = 0L
            })
        }
    }

    @ReactMethod
    fun startHardcoded(promise: Promise) {
        try {
            ensureController()

            val configJson = """
            {
              "log": { "loglevel": "warning" },
              "inbounds": [
                {
                  "port": 10808,
                  "listen": "127.0.0.1",
                  "protocol": "socks",
                  "settings": { "udp": true }
                }
              ],
              "outbounds": [
                { "protocol": "freedom", "settings": {} }
              ]
            }
            """.trimIndent()

            controller!!.startLoop(configJson)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("START_FAILED", e)
        }
    }

    @ReactMethod
    fun stop(promise: Promise) {
        try {
            controller?.stopLoop()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("STOP_FAILED", e)
        }
    }

    @ReactMethod
    fun startVpn(promise: Promise) {
        try {
            val activity = rc.currentActivity
            if (activity == null) {
                promise.reject("NO_ACTIVITY", "No current Activity")
                return
            }

            val prepareIntent = VpnService.prepare(activity)
            if (prepareIntent != null) {
                pendingVpnPromise = promise
                activity.startActivityForResult(prepareIntent, VPN_REQ_CODE)
                return
            }

            // already has permission
            startVpnService()
            promise.resolve("STARTED")
        } catch (e: Exception) {
            pendingVpnPromise = null
            promise.reject("VPN_START_FAILED", e)
        }
    }

    private fun startVpnService() {
        val i = Intent(rc, XrayVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rc.startForegroundService(i)
        } else {
            rc.startService(i)
        }
    }
}
