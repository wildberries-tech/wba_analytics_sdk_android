package ru.wildanalytics.pub.analytics.send.fallback

import ru.wildanalytics.pub.analytics.ApiUrl
import ru.wildanalytics.pub.analytics.network.NetworkSnapshot

/** Переключение батча на резервный URL: попытка [attempt] на [primaryUrl] упала с [cause]. */
internal data class FallbackSwitch(
    val primaryUrl: ApiUrl,
    val fallbackUrl: ApiUrl,
    val cause: Exception,
    val attempt: Int,
    val batchNumber: String,
    val network: NetworkSnapshot,
)
