package com.ultracoresoft.ultrasmsgateway.protocol

import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Frames as the server sends them (see the server's DeviceWebSocketTest and ServerFrames). */
class FramesTest {

    @Test
    fun parsesSendWithNanosecondExpiry() {
        val frame = Frames.parse(
            """{"type":"send","id":"5f0c","to":"+243812345678","body":"Hello","expiresAt":"2026-11-09T11:06:46.624046067Z"}"""
        )
        assertEquals(
            ServerFrame.Send("5f0c", "+243812345678", "Hello", Instant.parse("2026-11-09T11:06:46.624Z").toEpochMilli()),
            frame,
        )
    }

    @Test
    fun parsesHelloAndIgnoresUnknownFields() {
        val frame = Frames.parse("""{"type":"hello","deviceId":"8b1e","serverTime":"2026-11-09T10:15:00Z","maxInFlight":3,"future":true}""")
        assertEquals(ServerFrame.Hello("8b1e", 3), frame)
    }

    @Test
    fun parsesErrorWithNullId() {
        assertEquals(ServerFrame.Error("INVALID_FRAME", null), Frames.parse("""{"type":"error","code":"INVALID_FRAME","id":null}"""))
    }

    @Test
    fun keepsUnknownTypesForForwardCompatibility() {
        assertEquals(ServerFrame.Unknown("config"), Frames.parse("""{"type":"config"}"""))
    }

    @Test
    fun rejectsInvalidFrames() {
        assertNull(Frames.parse("not json"))
        assertNull(Frames.parse("""{"type":"send","id":"x"}"""))
        assertNull(Frames.parse("""{"type":"send","id":"x","to":"+1","body":"b","expiresAt":"tomorrow"}"""))
    }

    @Test
    fun writesReports() {
        assertEquals("ack", JSONObject(Frames.ack("m1")).getString("type"))
        assertEquals("m1", JSONObject(Frames.sent("m1")).getString("id"))
        val failed = JSONObject(Frames.failed("m1", "RESULT_ERROR_NO_SERVICE"))
        assertEquals("failed", failed.getString("type"))
        assertEquals("RESULT_ERROR_NO_SERVICE", failed.getString("reason"))
    }

    @Test
    fun heartbeatLeavesOutUnknownValues() {
        val json = JSONObject(Frames.heartbeat(Heartbeat(80, true, null, "Vodacom CD", null, "0.1.0")))
        assertEquals("heartbeat", json.getString("type"))
        assertEquals(80, json.getInt("battery"))
        assertEquals("Vodacom CD", json.getString("operator"))
        assertFalse(json.has("signal"))
        assertFalse(json.has("network"))
    }
}
