package com.ultracoresoft.ultrasmsgateway.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** SQLite implementation of [OutboxStore]. Two small tables do not justify an ORM. */
class SqliteOutboxStore(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), OutboxStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE message (
                id TEXT PRIMARY KEY,
                recipient TEXT NOT NULL,
                body TEXT,
                expires_at INTEGER NOT NULL,
                state TEXT NOT NULL,
                parts_total INTEGER NOT NULL DEFAULT 0,
                parts_sent INTEGER NOT NULL DEFAULT 0,
                parts_delivered INTEGER NOT NULL DEFAULT 0,
                failure_reason TEXT,
                received_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_message_state ON message (state, received_at)")
        db.execSQL(
            """
            CREATE TABLE report (
                report_id INTEGER PRIMARY KEY AUTOINCREMENT,
                message_id TEXT NOT NULL,
                type TEXT NOT NULL,
                reason TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // First version: nothing to migrate yet.
    }

    override fun find(id: String): OutboxMessage? =
        readableDatabase.query("message", null, "id = ?", arrayOf(id), null, null, null).use { cursor ->
            if (cursor.moveToFirst()) cursor.toMessage() else null
        }

    override fun insert(message: OutboxMessage) {
        writableDatabase.insertWithOnConflict("message", null, message.toValues(), SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun update(message: OutboxMessage) {
        writableDatabase.update("message", message.toValues(), "id = ?", arrayOf(message.id))
    }

    override fun nextReceived(): OutboxMessage? =
        readableDatabase.query(
            "message", null, "state = ?", arrayOf(MessageState.RECEIVED.name),
            null, null, "received_at ASC", "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toMessage() else null }

    override fun countInState(state: MessageState): Int =
        count("SELECT COUNT(*) FROM message WHERE state = ?", arrayOf(state.name))

    override fun countSentSince(sinceMillis: Long): Int = count(
        "SELECT COUNT(*) FROM message WHERE state IN (?, ?) AND updated_at >= ?",
        arrayOf(MessageState.SENT.name, MessageState.DELIVERED.name, sinceMillis.toString()),
    )

    override fun addReport(report: Report) {
        writableDatabase.insert("report", null, ContentValues().apply {
            put("message_id", report.messageId)
            put("type", report.type.name)
            put("reason", report.reason)
        })
    }

    override fun pendingReports(): List<Report> =
        readableDatabase.query("report", null, null, null, null, null, "report_id ASC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Report(
                            reportId = cursor.getLong(cursor.getColumnIndexOrThrow("report_id")),
                            messageId = cursor.getString(cursor.getColumnIndexOrThrow("message_id")),
                            type = ReportType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("type"))),
                            reason = cursor.getStringOrNull("reason"),
                        )
                    )
                }
            }
        }

    override fun deleteReport(reportId: Long) {
        writableDatabase.delete("report", "report_id = ?", arrayOf(reportId.toString()))
    }

    override fun purgeFinishedBefore(beforeMillis: Long) {
        writableDatabase.delete(
            "message",
            "state IN (?, ?, ?) AND updated_at < ?",
            arrayOf(
                MessageState.SENT.name, MessageState.DELIVERED.name, MessageState.FAILED.name,
                beforeMillis.toString(),
            ),
        )
    }

    private fun count(sql: String, args: Array<String>): Int =
        readableDatabase.rawQuery(sql, args).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun OutboxMessage.toValues() = ContentValues().apply {
        put("id", id)
        put("recipient", to)
        put("body", body)
        put("expires_at", expiresAtMillis)
        put("state", state.name)
        put("parts_total", partsTotal)
        put("parts_sent", partsSent)
        put("parts_delivered", partsDelivered)
        put("failure_reason", failureReason)
        put("received_at", receivedAtMillis)
        put("updated_at", updatedAtMillis)
    }

    private fun Cursor.toMessage() = OutboxMessage(
        id = getString(getColumnIndexOrThrow("id")),
        to = getString(getColumnIndexOrThrow("recipient")),
        body = getStringOrNull("body"),
        expiresAtMillis = getLong(getColumnIndexOrThrow("expires_at")),
        state = MessageState.valueOf(getString(getColumnIndexOrThrow("state"))),
        partsTotal = getInt(getColumnIndexOrThrow("parts_total")),
        partsSent = getInt(getColumnIndexOrThrow("parts_sent")),
        partsDelivered = getInt(getColumnIndexOrThrow("parts_delivered")),
        failureReason = getStringOrNull("failure_reason"),
        receivedAtMillis = getLong(getColumnIndexOrThrow("received_at")),
        updatedAtMillis = getLong(getColumnIndexOrThrow("updated_at")),
    )

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private companion object {
        const val DATABASE_NAME = "outbox.db"
        const val DATABASE_VERSION = 1
    }
}
