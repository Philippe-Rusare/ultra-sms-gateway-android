package com.ultracoresoft.ultrasmsgateway.engine

import com.ultracoresoft.ultrasmsgateway.data.MessageState
import com.ultracoresoft.ultrasmsgateway.data.OutboxMessage
import com.ultracoresoft.ultrasmsgateway.data.OutboxStore
import com.ultracoresoft.ultrasmsgateway.data.Report
import com.ultracoresoft.ultrasmsgateway.data.ReportType
import com.ultracoresoft.ultrasmsgateway.protocol.Frames
import com.ultracoresoft.ultrasmsgateway.protocol.ServerFrame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Where reports are written: the WebSocket when it is open. Returns false when nothing could be written. */
fun interface FrameSink {
    fun send(text: String): Boolean
}

/**
 * Message logic of the phone, independent of Android so that it can be unit tested:
 * receiving `send` frames, deduplication, expiry, aggregation of multipart results, and reports.
 * Reports are stored before being written, so that none is lost while the connection is down.
 */
class GatewayEngine(
    private val store: OutboxStore,
    private val sink: FrameSink,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onMessageQueued: () -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    /** Handles a `send` frame; other frames are not the engine's business. */
    suspend fun onSendFrame(frame: ServerFrame.Send) = locked {
        val now = clock()
        val existing = store.find(frame.id)
        when {
            existing != null -> {
                // Pushed again after a reconnection: confirm, and repeat the last known result.
                queueReport(frame.id, ReportType.ACK)
                repeatResult(existing)
            }
            frame.expiresAtMillis <= now -> {
                store.insert(newMessage(frame, now).copy(body = null, state = MessageState.FAILED, failureReason = EXPIRED_ON_DEVICE))
                queueReport(frame.id, ReportType.ACK)
                queueReport(frame.id, ReportType.FAILED, EXPIRED_ON_DEVICE)
            }
            else -> {
                store.insert(newMessage(frame, now))
                queueReport(frame.id, ReportType.ACK)
                onMessageQueued()
            }
        }
        flush()
    }

    /** Next message to send, oldest first. Messages that expired while waiting are failed on the way. */
    suspend fun nextToSend(): OutboxMessage? = locked {
        var result: OutboxMessage? = null
        while (result == null) {
            val candidate = store.nextReceived() ?: break
            if (candidate.expiresAtMillis <= clock() || candidate.body == null) {
                fail(candidate, EXPIRED_ON_DEVICE)
            } else {
                result = candidate
            }
        }
        flush()
        result
    }

    /** Called right before handing the parts to Android, so that their results find the message. */
    suspend fun markSending(id: String, parts: Int) = locked {
        store.find(id)?.takeIf { it.state == MessageState.RECEIVED }?.let { message ->
            store.update(message.copy(state = MessageState.SENDING, partsTotal = parts, updatedAtMillis = clock()))
        }
    }

    /** Android refused the message before sending anything (exception, missing permission). */
    suspend fun onSendError(id: String, reason: String) = locked {
        store.find(id)?.takeIf { it.state == MessageState.RECEIVED || it.state == MessageState.SENDING }
            ?.let { fail(it, reason) }
        flush()
    }

    /** Result of one part. The message is sent when every part is; one failed part fails it. */
    suspend fun onPartSent(id: String, success: Boolean, reason: String?) = locked {
        val message = store.find(id)?.takeIf { it.state == MessageState.SENDING } ?: return@locked
        if (!success) {
            fail(message, reason ?: "RESULT_ERROR_GENERIC_FAILURE")
        } else {
            val partsSent = message.partsSent + 1
            if (partsSent >= message.partsTotal) {
                markSent(message.copy(partsSent = partsSent))
            } else {
                store.update(message.copy(partsSent = partsSent, updatedAtMillis = clock()))
            }
        }
        flush()
    }

    /** Delivery report of one part. The message is delivered when every part is. */
    suspend fun onPartDelivered(id: String) = locked {
        var message = store.find(id) ?: return@locked
        if (message.state != MessageState.SENDING && message.state != MessageState.SENT) return@locked
        val partsDelivered = message.partsDelivered + 1
        if (partsDelivered < message.partsTotal) {
            store.update(message.copy(partsDelivered = partsDelivered, updatedAtMillis = clock()))
            return@locked
        }
        if (message.state == MessageState.SENDING) {
            // The delivery report overtook the sent result: the message was obviously sent.
            message = markSent(message.copy(partsSent = message.partsTotal))
        }
        store.update(message.copy(state = MessageState.DELIVERED, partsDelivered = partsDelivered, updatedAtMillis = clock()))
        queueReport(id, ReportType.DELIVERED)
        flush()
    }

    /** Writes the stored reports, in order, until the connection refuses one. */
    suspend fun flushReports() = locked { flush() }

    /** Counters for the screen. */
    suspend fun counters(sinceMillis: Long): Counters = withContext(io) {
        Counters(
            waiting = store.countInState(MessageState.RECEIVED) + store.countInState(MessageState.SENDING),
            sentSince = store.countSentSince(sinceMillis),
        )
    }

    /** Forgets finished messages after the deduplication window. */
    suspend fun purge() = locked { store.purgeFinishedBefore(clock() - RETENTION_MILLIS) }

    data class Counters(val waiting: Int, val sentSince: Int)

    // ------------------------------------------------------------------ helpers (call with the lock held)

    private fun repeatResult(message: OutboxMessage) {
        when (message.state) {
            MessageState.SENT -> queueReport(message.id, ReportType.SENT)
            MessageState.DELIVERED -> {
                queueReport(message.id, ReportType.SENT)
                queueReport(message.id, ReportType.DELIVERED)
            }
            MessageState.FAILED -> queueReport(message.id, ReportType.FAILED, message.failureReason ?: "UNKNOWN")
            MessageState.RECEIVED, MessageState.SENDING -> Unit
        }
    }

    private fun markSent(message: OutboxMessage): OutboxMessage {
        val sent = message.copy(state = MessageState.SENT, body = null, updatedAtMillis = clock())
        store.update(sent)
        queueReport(message.id, ReportType.SENT)
        return sent
    }

    private fun fail(message: OutboxMessage, reason: String) {
        store.update(
            message.copy(state = MessageState.FAILED, body = null, failureReason = reason, updatedAtMillis = clock())
        )
        queueReport(message.id, ReportType.FAILED, reason)
    }

    private fun queueReport(id: String, type: ReportType, reason: String? = null) {
        store.addReport(Report(messageId = id, type = type, reason = reason))
    }

    private fun flush() {
        for (report in store.pendingReports()) {
            val frame = when (report.type) {
                ReportType.ACK -> Frames.ack(report.messageId)
                ReportType.SENT -> Frames.sent(report.messageId)
                ReportType.DELIVERED -> Frames.delivered(report.messageId)
                ReportType.FAILED -> Frames.failed(report.messageId, report.reason ?: "UNKNOWN")
            }
            if (!sink.send(frame)) return
            store.deleteReport(report.reportId)
        }
    }

    private fun newMessage(frame: ServerFrame.Send, now: Long) = OutboxMessage(
        id = frame.id,
        to = frame.to,
        body = frame.body,
        expiresAtMillis = frame.expiresAtMillis,
        state = MessageState.RECEIVED,
        receivedAtMillis = now,
        updatedAtMillis = now,
    )

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(io) { mutex.withLock { block() } }

    companion object {
        const val EXPIRED_ON_DEVICE = "EXPIRED_ON_DEVICE"
        /** The server never pushes a message again after its time to live (24 h at most). */
        const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
