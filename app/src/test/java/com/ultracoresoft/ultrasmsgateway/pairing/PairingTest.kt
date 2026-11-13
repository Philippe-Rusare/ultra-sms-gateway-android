package com.ultracoresoft.ultrasmsgateway.pairing

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {

    private val code = "usgp_" + "a".repeat(43)

    @Test
    fun parsesTheLinkOfTheDashboard() {
        val link = PairingLink.parse("ultrasms://pair?server=https%3A%2F%2Fapp.ultrasmsgateway.io&code=$code")

        assertEquals(PairingLink("https://app.ultrasmsgateway.io", code), link)
        assertEquals("https://app.ultrasmsgateway.io/v1/device/pair", link?.pairUrl)
    }

    @Test
    fun acceptsALocalServerWithAPort() {
        val link = PairingLink.parse(" ultrasms://pair?server=http%3A%2F%2F10.0.2.2%3A8090&code=$code ")

        assertEquals("http://10.0.2.2:8090/v1/device/pair", link?.pairUrl)
    }

    @Test
    fun rejectsAnythingThatIsNotAPairingLink() {
        assertNull(PairingLink.parse(null))
        assertNull(PairingLink.parse("https://example.com/?server=https%3A%2F%2Fa.io&code=$code"))
        assertNull(PairingLink.parse("ultrasms://other?server=https%3A%2F%2Fa.io&code=$code"))
        assertNull(PairingLink.parse("ultrasms://pair?server=ftp%3A%2F%2Fa.io&code=$code"))
        assertNull(PairingLink.parse("ultrasms://pair?server=https%3A%2F%2Fa.io&code=usgd_not-a-pairing-code-000000"))
        assertNull(PairingLink.parse("ultrasms://pair?server=https%3A%2F%2Fa.io"))
        assertNull(PairingLink.parse("not a link at all"))
    }

    @Test
    fun exchangesTheCodeForATokenAndTheDeviceUrl() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"deviceId":"8b1e","name":"Vodacom phone 1","token":"usgd_secret-token","deviceUrl":"wss://device.example.com/v1/device/ws"}"""
                )
            )
            val link = PairingLink(server.url("/").toString().trimEnd('/'), code)

            val result = PairingClient().pair(link)

            assertEquals(PairingResult.Paired("wss://device.example.com/v1/device/ws", "usgd_secret-token", "Vodacom phone 1"), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/device/pair", request.path)
            assertEquals(code, JSONObject(request.body.readUtf8()).getString("code"))
        }
    }

    @Test
    fun aRefusedCodeIsReportedAsRejected() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"status":404}"""))

            assertEquals(PairingResult.Rejected, PairingClient().pair(PairingLink(server.url("/").toString(), code)))
        }
    }

    @Test
    fun anUnreachableOrOddServerIsReportedAsFailed() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setBody("<html>not json</html>"))
            server.enqueue(MockResponse().setBody("""{"token":"usgd_x","deviceUrl":"https://not-a-websocket"}"""))
            val link = PairingLink(server.url("/").toString(), code)

            assertEquals(PairingResult.Failed, PairingClient().pair(link))
            assertEquals(PairingResult.Failed, PairingClient().pair(link))
            assertEquals(PairingResult.Failed, PairingClient().pair(link))
        }
        assertEquals(PairingResult.Failed, PairingClient().pair(PairingLink("http://127.0.0.1:1", code)))
    }
}
