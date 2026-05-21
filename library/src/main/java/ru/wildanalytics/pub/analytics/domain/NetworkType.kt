package ru.wildanalytics.pub.analytics.domain

internal enum class NetworkType(val serializedName: String) {
    WiFi("Wi-Fi"),
    Ethernet("Ethernet"),
    Mobile2G("2G"),
    Mobile3G("3G"),
    Mobile4G("4G"),
    Mobile5G("5G"),
    Other("Other"),
}
