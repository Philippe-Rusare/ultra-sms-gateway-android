package com.ultracoresoft.ultrasmsgateway.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ultracoresoft.ultrasmsgateway.GatewayApp
import com.ultracoresoft.ultrasmsgateway.MainActivity
import com.ultracoresoft.ultrasmsgateway.R
import com.ultracoresoft.ultrasmsgateway.device.DeviceInfo
import com.ultracoresoft.ultrasmsgateway.engine.ConnectionState
import com.ultracoresoft.ultrasmsgateway.engine.GatewayStatus
import com.ultracoresoft.ultrasmsgateway.net.GatewayClient
import com.ultracoresoft.ultrasmsgateway.sms.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the gateway alive with the screen off: the WebSocket, the heartbeat and the
 * sending loop. It holds a partial wake lock, so the phone should stay on its charger.
 */
class GatewayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: GatewayApp
    private var client: GatewayClient? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = GatewayApp.from(this)
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(app.status.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            app.settings.enabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (client == null && !startGateway()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        client?.stop()
        client = null
        networkCallback?.let { callback ->
            runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
        }
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        app.link = null
        app.status.update { it.copy(state = ConnectionState.STOPPED, nextRetryAtMillis = null) }
        super.onDestroy()
    }

    private fun startGateway(): Boolean {
        val settings = app.settings
        val token = settings.token()
        if (!settings.isConfigured || token == null) {
            Log.w(TAG, "Not configured; stopping")
            return false
        }
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UltraSmsGateway:gateway")
            ?.apply { setReferenceCounted(false); acquire() }

        val deviceInfo = DeviceInfo(this, settings)
        client = GatewayClient(
            scope = scope,
            serverUrl = settings.serverUrl,
            token = token,
            engine = app.engine,
            status = app.status,
            heartbeat = deviceInfo::heartbeat,
            onLink = { app.link = it },
        ).also { it.start() }

        registerNetworkCallback()
        scope.launch { app.engine.purge() }
        scope.launch { sendLoop(SmsSender(this@GatewayService, settings)) }
        scope.launch { app.status.collect { updateNotification(it) } }
        return true
    }

    /** One SMS at a time, a few seconds apart: operators flag bursts from a single SIM. */
    private suspend fun sendLoop(sender: SmsSender) {
        while (scope.isActive) {
            val message = app.engine.nextToSend()
            if (message == null) {
                app.sendSignal.receive()
                continue
            }
            val body = message.body ?: continue
            try {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    app.engine.onSendError(message.id, "PERMISSION_DENIED")
                    continue
                }
                val parts = sender.split(body)
                app.engine.markSending(message.id, parts.size)
                sender.send(message.id, message.to, parts)
            } catch (e: SecurityException) {
                app.engine.onSendError(message.id, "PERMISSION_DENIED")
            } catch (e: RuntimeException) {
                app.engine.onSendError(message.id, "SEND_EXCEPTION_${e.javaClass.simpleName}")
            }
            delay(SEND_INTERVAL_MILLIS)
        }
    }

    private fun registerNetworkCallback() {
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                client?.onNetworkAvailable()
            }
        }
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun updateNotification(status: GatewayStatus) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(status))
    }

    private fun notification(status: GatewayStatus): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gateway)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(stateLabel(status.state)))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "GatewayService"
        private const val CHANNEL_ID = "gateway"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.ultracoresoft.ultrasmsgateway.STOP"
        private const val SEND_INTERVAL_MILLIS = 3_000L

        fun start(context: Context) {
            GatewayApp.from(context).settings.enabled = true
            ContextCompat.startForegroundService(context, Intent(context, GatewayService::class.java))
        }

        fun stop(context: Context) {
            GatewayApp.from(context).settings.enabled = false
            context.stopService(Intent(context, GatewayService::class.java))
        }

        fun stateLabel(state: ConnectionState): Int = when (state) {
            ConnectionState.STOPPED -> R.string.state_stopped
            ConnectionState.CONNECTING -> R.string.state_connecting
            ConnectionState.CONNECTED -> R.string.state_connected
            ConnectionState.WAITING_RETRY -> R.string.state_waiting_retry
            ConnectionState.UNAUTHORIZED -> R.string.state_unauthorized
            ConnectionState.REVOKED -> R.string.state_revoked
        }
    }
}
