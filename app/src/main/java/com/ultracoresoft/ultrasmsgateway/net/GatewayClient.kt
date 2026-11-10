package com.ultracoresoft.ultrasmsgateway.net

import android.util.Log
import com.ultracoresoft.ultrasmsgateway.engine.Backoff
import com.ultracoresoft.ultrasmsgateway.engine.ConnectionState
import com.ultracoresoft.ultrasmsgateway.engine.FrameSink
import com.ultracoresoft.ultrasmsgateway.engine.GatewayEngine
import com.ultracoresoft.ultrasmsgateway.engine.GatewayStatus
import com.ultracoresoft.ultrasmsgateway.protocol.Frames
import com.ultracoresoft.ultrasmsgateway.protocol.Heartbeat
import com.ultracoresoft.ultrasmsgateway.protocol.ServerFrame
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Keeps one WebSocket open to the gateway and reconnects with a growing delay. A connection attempt starts
 * right away when the network comes back ([onNetworkAvailable]). The token is sent in the Authorization
 * header and never logged.
 */
class GatewayClient(
    private val scope: CoroutineScope,
    private val serverUrl: String,
    private val token: String,
    private val engine: GatewayEngine,
    private val status: MutableStateFlow<GatewayStatus>,
    private val heartbeat: () -> Heartbeat,
    private val onLink: (FrameSink?) -> Unit,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(PING_SECONDS, TimeUnit.SECONDS)
        .build()

    private val networkSignal = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch { run() }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        onLink(null)
        status.update { it.copy(state = ConnectionState.STOPPED, nextRetryAtMillis = null) }
    }

    fun onNetworkAvailable() {
        networkSignal.trySend(Unit)
    }

    private suspend fun run() {
        var attempt = 0
        while (scope.isActive) {
            status.update { it.copy(state = ConnectionState.CONNECTING, nextRetryAtMillis = null) }
            val outcome = connectOnce { attempt = 0 }
            onLink(null)
            when (outcome) {
                Outcome.Unauthorized -> {
                    status.update { it.copy(state = ConnectionState.UNAUTHORIZED, lastError = "HTTP 401") }
                    return
                }
                Outcome.Revoked -> {
                    status.update { it.copy(state = ConnectionState.REVOKED, lastError = "Device revoked") }
                    return
                }
                is Outcome.Lost -> {
                    val wait = Backoff.delayMillis(attempt++)
                    status.update {
                        it.copy(
                            state = ConnectionState.WAITING_RETRY,
                            lastError = outcome.reason,
                            nextRetryAtMillis = System.currentTimeMillis() + wait,
                        )
                    }
                    // Wait for the delay, or less if the network comes back meanwhile.
                    while (networkSignal.tryReceive().isSuccess) Unit
                    withTimeoutOrNull(wait) { networkSignal.receive() }
                }
            }
        }
    }

    /** Opens one connection and suspends until it ends. */
    private suspend fun connectOnce(onOpened: () -> Unit): Outcome {
        val ended = CompletableDeferred<Outcome>()
        var heartbeatJob: Job? = null
        val request = Request.Builder()
            .url(serverUrl)
            .header("Authorization", "Bearer $token")
            .build()

        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onOpened()
                onLink(FrameSink { text -> webSocket.send(text) })
                status.update { it.copy(state = ConnectionState.CONNECTED, lastError = null) }
                scope.launch { engine.flushReports() }
                heartbeatJob = scope.launch {
                    while (isActive) {
                        webSocket.send(Frames.heartbeat(heartbeat()))
                        delay(HEARTBEAT_MILLIS)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when (val frame = Frames.parse(text)) {
                    is ServerFrame.Hello -> status.update { it.copy(deviceId = frame.deviceId) }
                    is ServerFrame.Send -> scope.launch { engine.onSendFrame(frame) }
                    is ServerFrame.Error -> Log.w(TAG, "Gateway error ${frame.code}")
                    is ServerFrame.Unknown -> Log.d(TAG, "Ignoring frame type ${frame.type}")
                    null -> Log.w(TAG, "Ignoring an invalid frame")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ended.complete(if (code == CLOSE_REVOKED) Outcome.Revoked else Outcome.Lost("Closed ($code)"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val outcome = when (response?.code) {
                    401 -> Outcome.Unauthorized
                    null -> Outcome.Lost(t.javaClass.simpleName)
                    else -> Outcome.Lost("HTTP ${response.code}")
                }
                ended.complete(outcome)
            }
        })

        return try {
            ended.await()
        } finally {
            heartbeatJob?.cancel()
            socket.cancel()
        }
    }

    private sealed interface Outcome {
        data object Unauthorized : Outcome
        data object Revoked : Outcome
        data class Lost(val reason: String) : Outcome
    }

    private companion object {
        const val TAG = "GatewayClient"
        const val PING_SECONDS = 30L
        const val HEARTBEAT_MILLIS = 30_000L
        const val NORMAL_CLOSURE = 1000
        const val CLOSE_REVOKED = 4001
    }
}
