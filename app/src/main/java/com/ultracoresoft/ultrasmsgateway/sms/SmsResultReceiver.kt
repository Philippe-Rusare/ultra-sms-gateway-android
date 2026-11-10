package com.ultracoresoft.ultrasmsgateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import com.ultracoresoft.ultrasmsgateway.GatewayApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives the sent and delivered results of every SMS part and passes them to the engine. */
class SmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
        val engine = GatewayApp.from(context).engine
        val code = resultCode
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_SENT -> engine.onPartSent(
                        id,
                        success = SmsResults.isSuccess(code),
                        reason = if (SmsResults.isSuccess(code)) null else SmsResults.reason(code),
                    )
                    ACTION_DELIVERED -> if (isDelivered(intent)) engine.onPartDelivered(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Without a readable report, assume delivery: Android only sends this broadcast when a report arrives. */
    private fun isDelivered(intent: Intent): Boolean {
        val pdu = intent.getByteArrayExtra("pdu") ?: return true
        val format = intent.getStringExtra("format")
        val status = runCatching { SmsMessage.createFromPdu(pdu, format)?.status }.getOrNull() ?: return true
        return SmsResults.isDelivered(status)
    }

    companion object {
        const val ACTION_SENT = "com.ultracoresoft.ultrasmsgateway.SMS_SENT"
        const val ACTION_DELIVERED = "com.ultracoresoft.ultrasmsgateway.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_PART = "part"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
