package ru.wildanalytics.pub.analytics.send.fallback

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.wildanalytics.pub.analytics.ApiKey
import ru.wildanalytics.pub.analytics.ApiUrl
import ru.wildanalytics.pub.analytics.domain.ApiData
import java.util.concurrent.ConcurrentHashMap

/**
 * Резервные URL по ключам и хосты, переключённые на них до перезапуска процесса.
 * Применяется только к URL с тем же path, поэтому прокси-URL локализаций не затрагиваются.
 * */
internal class EndpointFallback {

    private val activeHosts = ConcurrentHashMap.newKeySet<String>()
    private val registered = ConcurrentHashMap<ApiKey, ApiUrl>()

    /**
     * Пустой [fallbackUrl] не меняет уже зарегистрированный URL ключа: другой инстанс SDK не выключит переключение.
     * @return `false`, если непустой [fallbackUrl] невалиден: не URL или не https.
     * */
    fun register(apiKey: ApiKey, fallbackUrl: ApiUrl?): Boolean {
        if (fallbackUrl.isNullOrBlank()) return true
        val url = fallbackUrl.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return false
        registered[apiKey] = url.toString()
        return true
    }

    fun fallbackUrlOrNull(apiData: ApiData): ApiUrl? {
        val primary = apiData.apiUrl.toHttpUrlOrNull() ?: return null
        val fallback = registered[apiData.apiKey]?.toHttpUrlOrNull() ?: return null
        return fallback.takeIf { it.isApplicableTo(primary) }?.toString()
    }

    fun effectiveUrl(apiData: ApiData): ApiUrl {
        val host = apiData.apiUrl.toHttpUrlOrNull()?.host ?: return apiData.apiUrl
        if (host !in activeHosts) return apiData.apiUrl
        return fallbackUrlOrNull(apiData) ?: apiData.apiUrl
    }

    /** @return `true`, если хост переключён этим вызовом впервые за процесс. */
    fun activate(apiData: ApiData): Boolean {
        if (fallbackUrlOrNull(apiData) == null) return false
        val host = apiData.apiUrl.toHttpUrlOrNull()?.host ?: return false
        return activeHosts.add(host)
    }

    private fun HttpUrl.isApplicableTo(primary: HttpUrl): Boolean =
        host != primary.host && encodedPath == primary.encodedPath
}
