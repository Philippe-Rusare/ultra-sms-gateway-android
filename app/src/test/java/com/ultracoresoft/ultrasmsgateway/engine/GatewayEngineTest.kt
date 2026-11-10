package com.ultracoresoft.ultrasmsgateway.engine

import com.ultracoresoft.ultrasmsgateway.data.MessageState
import com.ultracoresoft.ultrasmsgateway.protocol.ServerFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GatewayEngineTest {

    private var now = 1_000_000L
    private lateinit var store: InMemoryOutboxStore
    private lateinit var engine: GatewayEngine
    private val written = mutableListOf<String>()
    private var online = true
    private var queuedSignals = 0

    @Before
    fun setUp() {
        store = InMemoryOutboxStore()
        engine = GatewayEngine(
            store = store,
            sink = { text -> if (online) written.add(text) else false },
            clock = { now },
            onMessageQueued = { queuedSignals++ },
            io = Dispatchers.Unconfined,
        )
    }

    private fun send(id: String = "m1", ttlMillis: Long = 60_000) =
        ServerFrame.Send(id = id, to = "+243812345678", body = "Your code is 123456", expiresAtMillis = now + ttlMillis)

    /** Written frames as "type:id[:reason]", for short assertions. */
    private fun frames(): List<String> = written.map { text ->
        val json = JSONObject(text)
        listOfNotNull(json.getString("type"), json.optString("id"), json.optString("reason").takeIf { it.isNotEmpty() })
            .joinToString(":")
    }

    @Test
    fun newMessageIsAcknowledgedStoredAndSignalled() = runTest {
        engine.onSendFrame(send())

        assertEquals(listOf("ack:m1"), frames())
        assertEquals(MessageState.RECEIVED, store.find("m1")?.state)
        assertEquals(1, queuedSignals)
    }

    @Test
    fun messageSentInOnePartIsReportedAndItsTextErased() = runTest {
        engine.onSendFrame(send())
        val next = engine.nextToSend()!!
        engine.markSending(next.id, parts = 1)
        engine.onPartSent(next.id, success = true, reason = null)

        assertEquals(listOf("ack:m1", "sent:m1"), frames())
        assertEquals(MessageState.SENT, store.find("m1")?.state)
        assertNull(store.find("m1")?.body)
    }

    @Test
    fun multipartMessageIsSentOnlyWhenEveryPartIs() = runTest {
        engine.onSendFrame(send())
        engine.markSending("m1", parts = 3)
        engine.onPartSent("m1", true, null)
        engine.onPartSent("m1", true, null)
        assertEquals(listOf("ack:m1"), frames())

        engine.onPartSent("m1", true, null)
        assertEquals(listOf("ack:m1", "sent:m1"), frames())
    }

    @Test
    fun oneFailedPartFailsTheMessageOnce() = runTest {
        engine.onSendFrame(send())
        engine.markSending("m1", parts = 2)
        engine.onPartSent("m1", false, "RESULT_ERROR_NO_SERVICE")
        engine.onPartSent("m1", true, null)

        assertEquals(listOf("ack:m1", "failed:m1:RESULT_ERROR_NO_SERVICE"), frames())
        assertNull(store.find("m1")?.body)
    }

    @Test
    fun deliveryIsReportedWhenEveryPartIsDelivered() = runTest {
        engine.onSendFrame(send())
        engine.markSending("m1", parts = 2)
        engine.onPartSent("m1", true, null)
        engine.onPartSent("m1", true, null)
        engine.onPartDelivered("m1")
        engine.onPartDelivered("m1")

        assertEquals(listOf("ack:m1", "sent:m1", "delivered:m1"), frames())
        assertEquals(MessageState.DELIVERED, store.find("m1")?.state)
    }

    @Test
    fun deliveryReportBeforeSentResultStillReportsBoth() = runTest {
        engine.onSendFrame(send())
        engine.markSending("m1", parts = 1)
        engine.onPartDelivered("m1")

        assertEquals(listOf("ack:m1", "sent:m1", "delivered:m1"), frames())
    }

    @Test
    fun messagePushedAgainIsNotSentTwiceAndItsResultIsRepeated() = runTest {
        engine.onSendFrame(send())
        engine.markSending("m1", parts = 1)
        engine.onPartSent("m1", true, null)
        written.clear()

        engine.onSendFrame(send())

        assertEquals(listOf("ack:m1", "sent:m1"), frames())
        assertNull(engine.nextToSend())
        assertEquals(1, queuedSignals)
    }

    @Test
    fun expiredMessageIsNeverSent() = runTest {
        engine.onSendFrame(send(ttlMillis = -1))

        assertEquals(listOf("ack:m1", "failed:m1:EXPIRED_ON_DEVICE"), frames())
        assertNull(engine.nextToSend())
        assertNull(store.find("m1")?.body)
    }

    @Test
    fun messageThatExpiresWhileWaitingIsFailedInsteadOfSent() = runTest {
        engine.onSendFrame(send(ttlMillis = 5_000))
        now += 10_000

        assertNull(engine.nextToSend())
        assertEquals(listOf("ack:m1", "failed:m1:EXPIRED_ON_DEVICE"), frames())
    }

    @Test
    fun reportsWaitWhileOfflineAndAreFlushedInOrder() = runTest {
        online = false
        engine.onSendFrame(send())
        engine.markSending("m1", parts = 1)
        engine.onPartSent("m1", true, null)
        assertTrue(written.isEmpty())

        online = true
        engine.flushReports()

        assertEquals(listOf("ack:m1", "sent:m1"), frames())
        engine.flushReports()
        assertEquals(2, written.size)
    }

    @Test
    fun queueIsFirstInFirstOut() = runTest {
        engine.onSendFrame(send("first"))
        now += 1
        engine.onSendFrame(send("second"))

        assertEquals("first", engine.nextToSend()?.id)
    }

    @Test
    fun sendErrorFailsTheMessage() = runTest {
        engine.onSendFrame(send())
        engine.onSendError("m1", "PERMISSION_DENIED")

        assertEquals(listOf("ack:m1", "failed:m1:PERMISSION_DENIED"), frames())
    }

    @Test
    fun finishedMessagesArePurgedAfterTheRetentionWindow() = runTest {
        engine.onSendFrame(send())
        engine.markSending("m1", parts = 1)
        engine.onPartSent("m1", true, null)

        now += GatewayEngine.RETENTION_MILLIS + 1
        engine.purge()

        assertNull(store.find("m1"))
    }

    @Test
    fun countersSeparateWaitingAndSent() = runTest {
        engine.onSendFrame(send("a"))
        engine.onSendFrame(send("b"))
        engine.markSending("a", parts = 1)
        engine.onPartSent("a", true, null)

        assertEquals(GatewayEngine.Counters(waiting = 1, sentSince = 1), engine.counters(sinceMillis = 0))
    }
}
