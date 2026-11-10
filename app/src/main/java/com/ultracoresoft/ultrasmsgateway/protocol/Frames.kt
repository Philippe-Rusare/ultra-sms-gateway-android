package com.ultracoresoft.ultrasmsgateway.protocol

import java.time.Instant
import java.time.format.DateTimeParseException
import org.json.JSONException
import org.json.JSONObject

/**
 * Frames of the device protocol. The contract is docs/device-protocol.md in the server repository:
 * unknown fields are ignored, so that the server can add some without breaking deployed apps.
 */
sealed interface ServerFrame {
    data class Hello(val deviceId: String, val maxInFlight: Int) : ServerFrame
    data class Send(val id: String, val to: String, val body: String, val expiresAtMillis: Long) : ServerFrame
    data class Error(val code: String, val id: String?) : ServerFrame
    data class Unknown(val type: String) : ServerFrame
}

/** Device state sent every 30 seconds. Every field is optional on the server side. */
data class Heartbeat(
    val battery: Int?,
    val charging: Boolean?,
    val signal: Int?,
    val operator: String?,
    val network: String?,
    val appVersion: String,
)

object Frames {

    /** Returns null when the text is not a valid frame. */
    fun parse(text: String): ServerFrame? = try {
        val json = JSONObject(text)
        when (val type = json.optString("type")) {
            "hello" -> ServerFrame.Hello(
                deviceId = json.getString("deviceId"),
                maxInFlight = json.optInt("maxInFlight", 1),
            )
            "send" -> ServerFrame.Send(
                id = json.getString("id"),
                to = json.getString("to"),
                body = json.getString("body"),
                expiresAtMillis = Instant.parse(json.getString("expiresAt")).toEpochMilli(),
            )
            "error" -> ServerFrame.Error(
                code = json.optString("code", "UNKNOWN"),
                id = json.optString("id").takeIf { it.isNotEmpty() && it != "null" },
            )
            else -> ServerFrame.Unknown(type)
        }
    } catch (e: JSONException) {
        null
    } catch (e: DateTimeParseException) {
        null
    }

    fun ack(id: String): String = report("ack", id)

    fun sent(id: String): String = report("sent", id)

    fun delivered(id: String): String = report("delivered", id)

    fun failed(id: String, reason: String): String =
        JSONObject().put("type", "failed").put("id", id).put("reason", reason).toString()

    fun heartbeat(heartbeat: Heartbeat): String = JSONObject().apply {
        put("type", "heartbeat")
        heartbeat.battery?.let { put("battery", it) }
        heartbeat.charging?.let { put("charging", it) }
        heartbeat.signal?.let { put("signal", it) }
        heartbeat.operator?.let { put("operator", it) }
        heartbeat.network?.let { put("network", it) }
        put("appVersion", heartbeat.appVersion)
    }.toString()

    private fun report(type: String, id: String): String =
        JSONObject().put("type", type).put("id", id).toString()
}
