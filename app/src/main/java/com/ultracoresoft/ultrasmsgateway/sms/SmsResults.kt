package com.ultracoresoft.ultrasmsgateway.sms

import android.telephony.SmsManager

/**
 * Names of the result codes of SmsManager, sent to the server as failure reasons.
 * Codes unknown to this version keep their number.
 */
object SmsResults {

    private const val RESULT_OK = -1

    private val names = mapOf(
        SmsManager.RESULT_ERROR_GENERIC_FAILURE to "RESULT_ERROR_GENERIC_FAILURE",
        SmsManager.RESULT_ERROR_RADIO_OFF to "RESULT_ERROR_RADIO_OFF",
        SmsManager.RESULT_ERROR_NULL_PDU to "RESULT_ERROR_NULL_PDU",
        SmsManager.RESULT_ERROR_NO_SERVICE to "RESULT_ERROR_NO_SERVICE",
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED to "RESULT_ERROR_LIMIT_EXCEEDED",
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE to "RESULT_ERROR_FDN_CHECK_FAILURE",
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED to "RESULT_ERROR_SHORT_CODE_NOT_ALLOWED",
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED to "RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED",
        SmsManager.RESULT_RADIO_NOT_AVAILABLE to "RESULT_RADIO_NOT_AVAILABLE",
        SmsManager.RESULT_NETWORK_REJECT to "RESULT_NETWORK_REJECT",
        SmsManager.RESULT_INVALID_ARGUMENTS to "RESULT_INVALID_ARGUMENTS",
        SmsManager.RESULT_INVALID_STATE to "RESULT_INVALID_STATE",
        SmsManager.RESULT_NO_MEMORY to "RESULT_NO_MEMORY",
        SmsManager.RESULT_INVALID_SMS_FORMAT to "RESULT_INVALID_SMS_FORMAT",
        SmsManager.RESULT_SYSTEM_ERROR to "RESULT_SYSTEM_ERROR",
        SmsManager.RESULT_MODEM_ERROR to "RESULT_MODEM_ERROR",
        SmsManager.RESULT_NETWORK_ERROR to "RESULT_NETWORK_ERROR",
        SmsManager.RESULT_ENCODING_ERROR to "RESULT_ENCODING_ERROR",
        SmsManager.RESULT_INVALID_SMSC_ADDRESS to "RESULT_INVALID_SMSC_ADDRESS",
        SmsManager.RESULT_OPERATION_NOT_ALLOWED to "RESULT_OPERATION_NOT_ALLOWED",
        SmsManager.RESULT_INTERNAL_ERROR to "RESULT_INTERNAL_ERROR",
        SmsManager.RESULT_NO_RESOURCES to "RESULT_NO_RESOURCES",
        SmsManager.RESULT_CANCELLED to "RESULT_CANCELLED",
        SmsManager.RESULT_REQUEST_NOT_SUPPORTED to "RESULT_REQUEST_NOT_SUPPORTED",
        SmsManager.RESULT_NO_BLUETOOTH_SERVICE to "RESULT_NO_BLUETOOTH_SERVICE",
        SmsManager.RESULT_INVALID_BLUETOOTH_ADDRESS to "RESULT_INVALID_BLUETOOTH_ADDRESS",
        SmsManager.RESULT_BLUETOOTH_DISCONNECTED to "RESULT_BLUETOOTH_DISCONNECTED",
        SmsManager.RESULT_UNEXPECTED_EVENT_STOP_SENDING to "RESULT_UNEXPECTED_EVENT_STOP_SENDING",
        SmsManager.RESULT_SMS_BLOCKED_DURING_EMERGENCY to "RESULT_SMS_BLOCKED_DURING_EMERGENCY",
        SmsManager.RESULT_SMS_SEND_RETRY_FAILED to "RESULT_SMS_SEND_RETRY_FAILED",
        SmsManager.RESULT_REMOTE_EXCEPTION to "RESULT_REMOTE_EXCEPTION",
        SmsManager.RESULT_NO_DEFAULT_SMS_APP to "RESULT_NO_DEFAULT_SMS_APP",
    )

    fun isSuccess(resultCode: Int): Boolean = resultCode == RESULT_OK

    fun reason(resultCode: Int): String = names[resultCode] ?: "RESULT_ERROR_$resultCode"

    /**
     * Delivery report status (TP-Status, 3GPP TS 23.040): below 0x20 the message reached the recipient,
     * 0x20 to 0x3F the network is still trying, 0x40 and above it gave up.
     */
    fun isDelivered(status: Int): Boolean = status in 0 until 0x20
}
