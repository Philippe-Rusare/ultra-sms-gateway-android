package com.ultracoresoft.ultrasmsgateway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ultracoresoft.ultrasmsgateway.ui.GatewayScreen
import com.ultracoresoft.ultrasmsgateway.ui.theme.UltraSmsGatewayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UltraSmsGatewayTheme {
                GatewayScreen()
            }
        }
    }
}
