package com.ultracoresoft.ultrasmsgateway

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ultracoresoft.ultrasmsgateway.ui.GatewayScreen
import com.ultracoresoft.ultrasmsgateway.ui.theme.UltraSmsGatewayTheme

class MainActivity : ComponentActivity() {

    /** Pairing link the app was opened with (`ultrasms://pair?...`), until the screen has handled it. */
    private var incomingPairingLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            incomingPairingLink = intent?.dataString
        }
        setContent {
            UltraSmsGatewayTheme {
                GatewayScreen(
                    incomingPairingLink = incomingPairingLink,
                    onPairingLinkHandled = { incomingPairingLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingPairingLink = intent.dataString
    }
}
