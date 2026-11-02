package com.wifidashboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DashboardColors = darkColorScheme(
    primary = SignalCyan,
    onPrimary = Color(0xFF003639),
    secondary = Color(0xFF9FCBFF),
    onSecondary = Color(0xFF00315D),
    tertiary = Amber,
    onTertiary = Color(0xFF432C00),
    background = Midnight,
    onBackground = TextPrimary,
    surface = Color(0xFF0B1720),
    onSurface = TextPrimary,
    surfaceVariant = Panel,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelRaised,
    tertiaryContainer = Color(0xFF493800),
    onTertiaryContainer = Color(0xFFFFE3A9),
    error = AlertRed,
)

@Composable
fun WifiDashboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DashboardColors,
        content = content,
    )
}
