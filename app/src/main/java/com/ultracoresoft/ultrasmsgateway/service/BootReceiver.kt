package com.ultracoresoft.ultrasmsgateway.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ultracoresoft.ultrasmsgateway.GatewayApp

/** Starts the gateway again after a reboot or an app update, if the user had left it running. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val settings = GatewayApp.from(context).settings
        if (settings.enabled && settings.isConfigured) {
            GatewayService.start(context)
        }
    }
}
