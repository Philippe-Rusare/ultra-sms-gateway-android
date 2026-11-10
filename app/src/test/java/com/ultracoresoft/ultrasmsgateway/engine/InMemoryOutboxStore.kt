package com.ultracoresoft.ultrasmsgateway.engine

import com.ultracoresoft.ultrasmsgateway.data.MessageState
import com.ultracoresoft.ultrasmsgateway.data.OutboxMessage
import com.ultracoresoft.ultrasmsgateway.data.OutboxStore
import com.ultracoresoft.ultrasmsgateway.data.Report

/** Test double of the SQLite store, with the same ordering rules. */
class InMemoryOutboxStore : OutboxStore {

    val messages = linkedMapOf<String, OutboxMessage>()
    private val reports = mutableListOf<Report>()
    private var nextReportId = 1L

    override fun find(id: String) = messages[id]

    override fun insert(message: OutboxMessage) {
        messages.putIfAbsent(message.id, message)
    }

    override fun update(message: OutboxMessage) {
        if (messages.containsKey(message.id)) messages[message.id] = message
    }

    override fun nextReceived() =
        messages.values.filter { it.state == MessageState.RECEIVED }.minByOrNull { it.receivedAtMillis }

    override fun countInState(state: MessageState) = messages.values.count { it.state == state }

    override fun countSentSince(sinceMillis: Long) = messages.values.count {
        (it.state == MessageState.SENT || it.state == MessageState.DELIVERED) && it.updatedAtMillis >= sinceMillis
    }

    override fun addReport(report: Report) {
        reports += report.copy(reportId = nextReportId++)
    }

    override fun pendingReports() = reports.toList()

    override fun deleteReport(reportId: Long) {
        reports.removeAll { it.reportId == reportId }
    }

    override fun purgeFinishedBefore(beforeMillis: Long) {
        messages.values.removeAll {
            it.state in setOf(MessageState.SENT, MessageState.DELIVERED, MessageState.FAILED) && it.updatedAtMillis < beforeMillis
        }
    }
}
