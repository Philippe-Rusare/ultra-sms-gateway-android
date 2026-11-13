package com.ultracoresoft.ultrasmsgateway.pairing

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

sealed interface PairingResult {
    /** The server created the device: [token] is shown once and must be stored. */
    data class Paired(val deviceUrl: String, val token: String, val name: String) : PairingResult

    /** The code is unknown, expired or already used. */
    data object Rejected : PairingResult

    /** The server could not be reached, or answered something unexpected. */
    data object Failed : PairingResult
}

/** Exchanges the one-time code of a [PairingLink] for a device token. Blocking: call it off the main thread. */
class PairingClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {

    fun pair(link: PairingLink): PairingResult {
        val request = try {
            Request.Builder()
                .url(link.pairUrl)
                .post(JSONObject().put("code", link.code).toString().toRequestBody(JSON))
                .build()
        } catch (e: IllegalArgumentException) {
            return PairingResult.Failed
        }
        return try {
            http.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> parse(response.body?.string().orEmpty())
                    response.code == 404 -> PairingResult.Rejected
                    else -> PairingResult.Failed
                }
            }
        } catch (e: IOException) {
            PairingResult.Failed
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        fun parse(body: String): PairingResult = try {
            val json = JSONObject(body)
            val deviceUrl = json.getString("deviceUrl")
            val token = json.getString("token")
            if ((deviceUrl.startsWith("wss://") || deviceUrl.startsWith("ws://")) && token.startsWith("usgd_")) {
                PairingResult.Paired(deviceUrl, token, json.optString("name"))
            } else {
                PairingResult.Failed
            }
        } catch (e: JSONException) {
            PairingResult.Failed
        }
    }
}
