package com.ultracoresoft.ultrasmsgateway.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import com.ultracoresoft.ultrasmsgateway.config.GatewaySettings

/** Hands a message to Android. Results arrive later in [SmsResultReceiver], one broadcast per part. */
class SmsSender(private val context: Context, private val settings: GatewaySettings) {

    fun split(body: String): List<String> = smsManager().divideMessage(body)

    fun send(id: String, to: String, parts: List<String>) {
        val sent = ArrayList<PendingIntent>(parts.size)
        val delivered = ArrayList<PendingIntent>(parts.size)
        parts.indices.forEach { part ->
            sent += resultIntent(SmsResultReceiver.ACTION_SENT, id, part)
            delivered += resultIntent(SmsResultReceiver.ACTION_DELIVERED, id, part)
        }
        smsManager().sendMultipartTextMessage(to, null, ArrayList(parts), sent, delivered)
    }

    private fun smsManager(): SmsManager {
        val subscription = settings.subscriptionId
        val manager = context.getSystemService(SmsManager::class.java)
        return when {
            subscription == GatewaySettings.DEFAULT_SIM -> manager ?: legacyDefault()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager != null -> manager.createForSubscriptionId(subscription)
            else -> legacyForSubscription(subscription)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyDefault(): SmsManager = SmsManager.getDefault()

    @Suppress("DEPRECATION")
    private fun legacyForSubscription(subscription: Int): SmsManager = SmsManager.getSmsManagerForSubscriptionId(subscription)

    /**
     * One distinct intent per message, part and kind: the data URI makes them different for the system,
     * otherwise their extras would overwrite each other. Mutable because Android adds the delivery PDU
     * and the error code to it; the explicit component keeps it private to this app.
     */
    private fun resultIntent(action: String, id: String, part: Int): PendingIntent {
        val intent = Intent(context, SmsResultReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("ultrasms://$action/$id/$part"))
            .putExtra(SmsResultReceiver.EXTRA_MESSAGE_ID, id)
            .putExtra(SmsResultReceiver.EXTRA_PART, part)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}
