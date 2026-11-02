package com.wifidashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wifidashboard.ui.WifiDashboardApp
import com.wifidashboard.ui.theme.WifiDashboardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WifiDashboardTheme {
                WifiDashboardApp()
            }
        }
    }
}
