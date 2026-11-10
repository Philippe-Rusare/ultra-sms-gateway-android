package com.ultracoresoft.ultrasmsgateway.config

import com.ultracoresoft.ultrasmsgateway.engine.Backoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun serverUrlMustBeAWebSocketUrl() {
        assertTrue(GatewaySettings.isValidServerUrl("wss://sms.example.com/v1/device/ws"))
        assertTrue(GatewaySettings.isValidServerUrl(" ws://10.0.2.2:8080/v1/device/ws "))
        assertFalse(GatewaySettings.isValidServerUrl("https://sms.example.com"))
        assertFalse(GatewaySettings.isValidServerUrl("wss://"))
    }

    @Test
    fun tokenMustBeADeviceToken() {
        assertTrue(GatewaySettings.isValidToken("usgd_" + "a".repeat(43)))
        assertFalse(GatewaySettings.isValidToken("usg_" + "a".repeat(43)))
        assertFalse(GatewaySettings.isValidToken("usgd_short"))
    }

    @Test
    fun backoffDoublesUpToOneMinute() {
        assertEquals(listOf(1L, 2L, 4L, 8L, 16L, 32L, 60L, 60L), (0..7).map { Backoff.delayMillis(it) / 1000 })
    }
}
