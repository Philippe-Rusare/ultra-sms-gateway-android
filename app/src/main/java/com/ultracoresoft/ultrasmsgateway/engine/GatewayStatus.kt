package com.ultracoresoft.ultrasmsgateway.engine

/** State of the connection to the gateway, shown on the screen and in the notification. */
enum class ConnectionState {
    /** The user stopped the gateway. */
    STOPPED,
    CONNECTING,
    CONNECTED,
    /** Disconnected; a new attempt is planned. */
    WAITING_RETRY,
    /** The server refused the token (401). Retrying is pointless until the token changes. */
    UNAUTHORIZED,
    /** The device was revoked in the dashboard (close code 4001). */
    REVOKED,
}

data class GatewayStatus(
    val state: ConnectionState = ConnectionState.STOPPED,
    val deviceId: String? = null,
    /** Short technical reason of the last disconnection, never a token or a message text. */
    val lastError: String? = null,
    val nextRetryAtMillis: Long? = null,
)

/** Delay before the next connection attempt: 1 s, 2 s, 4 s… up to 60 s. */
object Backoff {
    private const val MAX_SECONDS = 60L

    fun delayMillis(attempt: Int): Long {
        val seconds = if (attempt >= 6) MAX_SECONDS else minOf(MAX_SECONDS, 1L shl attempt.coerceAtLeast(0))
        return seconds * 1000
    }
}
