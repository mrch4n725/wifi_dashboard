package com.wifidashboard.network

object WifiChannel {
    fun fromFrequencyMhz(frequencyMhz: Int): Int? = when (frequencyMhz) {
        in 2412..2472 -> (frequencyMhz - 2407) / 5
        2484 -> 14
        in 5160..5885 -> (frequencyMhz - 5000) / 5
        in 5955..7115 -> (frequencyMhz - 5950) / 5
        else -> null
    }

    fun bandFromFrequencyMhz(frequencyMhz: Int): String? = when (frequencyMhz) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5895 -> "5 GHz"
        in 5950..7120 -> "6 GHz"
        else -> null
    }
}
