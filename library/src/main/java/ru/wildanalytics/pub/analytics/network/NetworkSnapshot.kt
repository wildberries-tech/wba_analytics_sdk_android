package ru.wildanalytics.pub.analytics.network

/**
 * Снимок активной сети для решения о переключении на резервный URL и для диагностики.
 * @property transports транспорты активной сети через запятую (`wifi,vpn`) или `none`.
 * @property validated прошла ли сеть проверку доступа в интернет, `null` если неизвестно.
 * */
internal data class NetworkSnapshot(
    val transports: String,
    val validated: Boolean?,
) {
    val hasInternet: Boolean
        get() = validated == true
}
