package com.ultracoresoft.ultrasmsgateway.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ultracoresoft.ultrasmsgateway.BuildConfig
import com.ultracoresoft.ultrasmsgateway.GatewayApp
import com.ultracoresoft.ultrasmsgateway.R
import com.ultracoresoft.ultrasmsgateway.config.GatewaySettings
import com.ultracoresoft.ultrasmsgateway.device.SimCard
import com.ultracoresoft.ultrasmsgateway.device.SimCards
import com.ultracoresoft.ultrasmsgateway.engine.ConnectionState
import com.ultracoresoft.ultrasmsgateway.engine.GatewayEngine
import com.ultracoresoft.ultrasmsgateway.service.GatewayService
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The only screen: state, server settings, permissions, start and stop. */
@Composable
fun GatewayScreen() {
    val context = LocalContext.current
    val app = remember { GatewayApp.from(context) }
    val settings = app.settings
    val status by app.status.collectAsStateWithLifecycle()

    var permissions by remember { mutableStateOf(PermissionState.read(context)) }
    var sims by remember { mutableStateOf(SimCards.active(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissions = PermissionState.read(context)
        sims = SimCards.active(context)
    }

    var counters by remember { mutableStateOf(GatewayEngine.Counters(0, 0)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            counters = app.engine.counters(startOfToday())
            now = System.currentTimeMillis()
            delay(2_000)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissions = PermissionState.read(context)
        sims = SimCards.active(context)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(40.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }

            StatusCard(status.state, status.deviceId, status.lastError, status.nextRetryAtMillis, now, counters)

            ServerCard(settings, sims, permissions.phone) {
                if (settings.enabled) {
                    // Apply the new settings to the running gateway.
                    GatewayService.stop(context)
                    GatewayService.start(context)
                }
            }

            PermissionsCard(
                permissions = permissions,
                onRequest = { wanted -> permissionLauncher.launch(wanted.toTypedArray()) },
                onBattery = { requestBatteryExemption(context) },
            )

            val running = status.state != ConnectionState.STOPPED
            val canStart = settings.isConfigured && permissions.sms
            if (running) {
                OutlinedButton(onClick = { GatewayService.stop(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.stop))
                }
            } else {
                Button(onClick = { GatewayService.start(context) }, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.start))
                }
                if (!canStart) {
                    Text(stringResource(R.string.start_hint), style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(
                stringResource(R.string.footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(
    state: ConnectionState,
    deviceId: String?,
    lastError: String?,
    nextRetryAtMillis: Long?,
    now: Long,
    counters: GatewayEngine.Counters,
) {
    val color = when (state) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionState.UNAUTHORIZED, ConnectionState.REVOKED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.section_status), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(GatewayService.stateLabel(state)), color = color, fontWeight = FontWeight.SemiBold)
            if (state == ConnectionState.WAITING_RETRY && nextRetryAtMillis != null) {
                val seconds = ((nextRetryAtMillis - now) / 1000).coerceAtLeast(0).toInt()
                Text(stringResource(R.string.status_retry_in, seconds), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(stringResource(R.string.status_sent_today), counters.sentSince.toString())
                Metric(stringResource(R.string.status_waiting), counters.waiting.toString())
            }
            deviceId?.let { LabelValue(stringResource(R.string.status_device), it.take(8) + "…") }
            lastError?.let { LabelValue(stringResource(R.string.status_last_error), it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerCard(settings: GatewaySettings, sims: List<SimCard>, canReadSims: Boolean, onSaved: () -> Unit) {
    var url by remember { mutableStateOf(settings.serverUrl) }
    var token by remember { mutableStateOf("") }
    var subscription by remember { mutableIntStateOf(settings.subscriptionId) }
    var hasToken by remember { mutableStateOf(settings.hasToken) }
    var message by remember { mutableStateOf<Int?>(null) }
    var simMenuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.section_server), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; message = null },
                label = { Text(stringResource(R.string.server_url_label)) },
                placeholder = { Text("wss://sms.example.com/v1/device/ws") },
                supportingText = {
                    Text(stringResource(if (url.trim().startsWith("ws://")) R.string.warning_cleartext else R.string.server_url_hint))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; message = null },
                label = { Text(stringResource(R.string.token_label)) },
                supportingText = { Text(stringResource(if (hasToken) R.string.token_saved else R.string.token_none)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!canReadSims) {
                Text(stringResource(R.string.sim_permission_needed), style = MaterialTheme.typography.bodySmall)
            } else if (sims.size > 1) {
                val selected = sims.firstOrNull { it.subscriptionId == subscription }?.label ?: stringResource(R.string.sim_default)
                ExposedDropdownMenuBox(expanded = simMenuOpen, onExpandedChange = { simMenuOpen = it }) {
                    OutlinedTextField(
                        value = selected,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.sim_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = simMenuOpen) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = simMenuOpen, onDismissRequest = { simMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sim_default)) },
                            onClick = { subscription = GatewaySettings.DEFAULT_SIM; simMenuOpen = false },
                        )
                        sims.forEach { sim ->
                            DropdownMenuItem(
                                text = { Text(sim.label) },
                                onClick = { subscription = sim.subscriptionId; simMenuOpen = false },
                            )
                        }
                    }
                }
            } else if (sims.size == 1) {
                LabelValue(stringResource(R.string.sim_label), sims.first().label)
            }

            message?.let {
                Text(
                    stringResource(it),
                    color = if (it == R.string.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = {
                    when {
                        !GatewaySettings.isValidServerUrl(url) -> message = R.string.error_url
                        token.isNotBlank() && !GatewaySettings.isValidToken(token) -> message = R.string.error_token
                        token.isBlank() && !hasToken -> message = R.string.error_token
                        else -> scope.launch {
                            settings.serverUrl = url
                            if (token.isNotBlank()) settings.saveToken(token)
                            settings.subscriptionId = subscription
                            token = ""
                            hasToken = true
                            message = R.string.saved
                            onSaved()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun PermissionsCard(permissions: PermissionState, onRequest: (List<String>) -> Unit, onBattery: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.section_permissions), style = MaterialTheme.typography.titleMedium)
            PermissionRow(stringResource(R.string.perm_sms), permissions.sms) { onRequest(listOf(Manifest.permission.SEND_SMS)) }
            PermissionRow(stringResource(R.string.perm_phone), permissions.phone) { onRequest(listOf(Manifest.permission.READ_PHONE_STATE)) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(stringResource(R.string.perm_notifications), permissions.notifications) {
                    onRequest(listOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }
            PermissionRow(stringResource(R.string.perm_battery), permissions.battery, onBattery)
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        if (granted) {
            Text(stringResource(R.string.granted), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        } else {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onGrant, colors = ButtonDefaults.outlinedButtonColors()) {
                Text(stringResource(R.string.grant))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** What the app is currently allowed to do. */
private data class PermissionState(val sms: Boolean, val phone: Boolean, val notifications: Boolean, val battery: Boolean) {
    companion object {
        fun read(context: Context) = PermissionState(
            sms = granted(context, Manifest.permission.SEND_SMS),
            phone = granted(context, Manifest.permission.READ_PHONE_STATE),
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                granted(context, Manifest.permission.POST_NOTIFICATIONS),
            battery = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true,
        )

        private fun granted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

/** The app is installed by hand, not from the Play Store, so asking for the exemption directly is fine. */
@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

private fun startOfToday(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
