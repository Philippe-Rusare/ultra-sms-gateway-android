package com.ultracoresoft.ultrasmsgateway.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/** An active SIM card, for the SIM picker. */
data class SimCard(val subscriptionId: Int, val label: String)

object SimCards {

    /** Active SIM cards; empty without the phone state permission. */
    fun active(context: Context): List<SimCard> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        return try {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                .orEmpty()
                .map { info ->
                    val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: info.displayName?.toString().orEmpty()
                    SimCard(info.subscriptionId, "SIM ${info.simSlotIndex + 1} · $carrier")
                }
        } catch (e: SecurityException) {
            emptyList()
        }
    }
}
