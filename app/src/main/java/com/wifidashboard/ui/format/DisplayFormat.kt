package com.wifidashboard.ui.format

import java.util.Locale

fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "Unavailable"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    do {
        value /= 1024.0
        unitIndex++
    } while (value >= 1024.0 && unitIndex < units.lastIndex)
    return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
}

fun String?.orUnavailable(): String = this?.takeIf(String::isNotBlank) ?: "Unavailable"
