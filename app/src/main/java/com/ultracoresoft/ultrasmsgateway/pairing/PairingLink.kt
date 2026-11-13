package com.ultracoresoft.ultrasmsgateway.pairing

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Content of the QR code shown by the dashboard: `ultrasms://pair?server=<dashboard address>&code=<one-time code>`.
 * See docs/device-protocol.md ("Pairing") in the server repository.
 */
data class PairingLink(val server: String, val code: String) {

    /** Address the code is exchanged at. */
    val pairUrl: String get() = server.trimEnd('/') + "/v1/device/pair"

    companion object {
        private const val SCHEME = "ultrasms"
        private const val HOST = "pair"
        private const val CODE_PREFIX = "usgp_"

        /** Returns null when the text is not a pairing link of this app. */
        fun parse(text: String?): PairingLink? {
            val uri = try {
                URI(text?.trim().orEmpty())
            } catch (e: URISyntaxException) {
                return null
            }
            if (!SCHEME.equals(uri.scheme, ignoreCase = true) || !HOST.equals(uri.host, ignoreCase = true)) return null
            val parameters = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) null else pair.substring(0, index) to URLDecoder.decode(pair.substring(index + 1), "UTF-8")
            }.toMap()
            val server = parameters["server"].orEmpty()
            val code = parameters["code"].orEmpty()
            val validServer = server.startsWith("https://") || server.startsWith("http://")
            return if (validServer && code.startsWith(CODE_PREFIX) && code.length > 20) PairingLink(server, code) else null
        }
    }
}
