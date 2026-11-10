package com.ultracoresoft.ultrasmsgateway.data

/** Life cycle of a message on the phone. */
enum class MessageState { RECEIVED, SENDING, SENT, DELIVERED, FAILED }

/**
 * A message received from the gateway. [body] is erased once the message is sent or failed: it often
 * holds a one-time code. The row itself is kept a few days so that a message pushed again by the server
 * is recognised instead of being sent twice.
 */
data class OutboxMessage(
    val id: String,
    val to: String,
    val body: String?,
    val expiresAtMillis: Long,
    val state: MessageState,
    val partsTotal: Int = 0,
    val partsSent: Int = 0,
    val partsDelivered: Int = 0,
    val failureReason: String? = null,
    val receivedAtMillis: Long,
    val updatedAtMillis: Long,
)

enum class ReportType { ACK, SENT, DELIVERED, FAILED }

/** A report for the server, kept until it could be written to the connection. */
data class Report(
    val reportId: Long = 0,
    val messageId: String,
    val type: ReportType,
    val reason: String? = null,
)

/** Persistent storage of messages and pending reports. Calls are blocking: use them off the main thread. */
interface OutboxStore {
    fun find(id: String): OutboxMessage?

    fun insert(message: OutboxMessage)

    fun update(message: OutboxMessage)

    /** Oldest message waiting to be sent, if any. */
    fun nextReceived(): OutboxMessage?

    fun countInState(state: MessageState): Int

    fun countSentSince(sinceMillis: Long): Int

    fun addReport(report: Report)

    fun pendingReports(): List<Report>

    fun deleteReport(reportId: Long)

    /** Removes finished messages last updated before the given time. */
    fun purgeFinishedBefore(beforeMillis: Long)
}
