package ru.wildberries.pub.analytics.send.fallback

import ru.wildberries.pub.analytics.ApiUrl
import ru.wildberries.pub.analytics.network.NetworkSnapshot

/** Переключение батча на резервный URL: попытка [attempt] на [primaryUrl] упала с [cause]. */
internal data class FallbackSwitch(
    val primaryUrl: ApiUrl,
    val fallbackUrl: ApiUrl,
    val cause: Exception,
    val attempt: Int,
    val batchNumber: String,
    val network: NetworkSnapshot,
)
