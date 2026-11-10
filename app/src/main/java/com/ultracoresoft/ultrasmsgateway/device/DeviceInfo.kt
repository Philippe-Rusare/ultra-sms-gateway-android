package com.ultracoresoft.ultrasmsgateway.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.ultracoresoft.ultrasmsgateway.BuildConfig
import com.ultracoresoft.ultrasmsgateway.config.GatewaySettings
import com.ultracoresoft.ultrasmsgateway.protocol.Heartbeat

/** Reads the phone state sent in heartbeats. Every value is optional: a missing permission just leaves it out. */
class DeviceInfo(private val context: Context, private val settings: GatewaySettings) {

    fun heartbeat(): Heartbeat {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val telephony = telephony()
        return Heartbeat(
            battery = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = battery?.let { plugged != 0 },
            signal = safely { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) telephony?.signalStrength?.level else null },
            operator = safely { telephony?.networkOperatorName?.takeIf { it.isNotBlank() } },
            network = network(),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    private fun telephony(): TelephonyManager? {
        val manager = context.getSystemService(TelephonyManager::class.java) ?: return null
        val subscription = settings.subscriptionId
        return if (subscription != GatewaySettings.DEFAULT_SIM && subscription != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            manager.createForSubscriptionId(subscription)
        } else {
            manager
        }
    }

    private fun network(): String? = safely {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        when {
            capabilities == null -> null
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
    }

    private fun <T> safely(block: () -> T?): T? = try {
        block()
    } catch (e: SecurityException) {
        null
    }
}
