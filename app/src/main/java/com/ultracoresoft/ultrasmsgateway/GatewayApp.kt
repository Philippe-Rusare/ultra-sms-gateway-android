package com.ultracoresoft.ultrasmsgateway

import android.app.Application
import android.content.Context
import com.ultracoresoft.ultrasmsgateway.config.GatewaySettings
import com.ultracoresoft.ultrasmsgateway.data.SqliteOutboxStore
import com.ultracoresoft.ultrasmsgateway.engine.FrameSink
import com.ultracoresoft.ultrasmsgateway.engine.GatewayEngine
import com.ultracoresoft.ultrasmsgateway.engine.GatewayStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide objects. The engine lives here, not in the service, because SMS results are delivered
 * to a broadcast receiver that may run while the service is restarting.
 */
class GatewayApp : Application() {

    lateinit var settings: GatewaySettings
        private set
    lateinit var engine: GatewayEngine
        private set

    val status = MutableStateFlow(GatewayStatus())

    /** Signals the sending loop that a message is waiting. */
    val sendSignal = Channel<Unit>(Channel.CONFLATED)

    /** The open connection, if any; reports wait in the database while it is null. */
    @Volatile
    var link: FrameSink? = null

    override fun onCreate() {
        super.onCreate()
        settings = GatewaySettings(this)
        engine = GatewayEngine(
            store = SqliteOutboxStore(this),
            sink = { text -> link?.send(text) ?: false },
            onMessageQueued = { sendSignal.trySend(Unit) },
        )
    }

    companion object {
        fun from(context: Context): GatewayApp = context.applicationContext as GatewayApp
    }
}
