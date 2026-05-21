package ru.wildanalytics.pub.analytics

import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.wildanalytics.pub.attribution.api.AttributionStrategy
import kotlin.time.Duration

/**
 * Значение идентификатора счётчика (будет выдано командой аналитики). Данный
 * параметр необходим для того, чтобы различать события от различных счётчиков.
 */
public typealias ApiKey = String

/**
 * Url сервиса аналитики.
 */
public typealias ApiUrl = String

/**
 * Позволяет переопределить стандартный логгер (например для отправки non-fatal ошибок).
 */
@Volatile
public var analyticsCustomLogger: WildAnalyticsLogger? = null

internal const val DEFAULT_PROD_URL = "https://wba.wb.ru/m/batch"

/**
 * @param attributionStrategy стратегия проверки атрибуции.
 * По умолчанию не проверяем.
 * */
public fun WildAnalytics(
    context: Context,
    apiUrlProvider: () -> ApiUrl = { DEFAULT_PROD_URL },
    apiKey: ApiKey,
    isCollectionEnabled: Boolean = true,
    attributionStrategy: AttributionStrategy = AttributionStrategy.NotCheckAttribution,
): WildAnalytics {
    require(apiKey.isNotEmpty())
    val wildAnalyticsLocator = WildAnalyticsServiceLocator.getInstance(context)
    val analytics = WildAnalyticsImpl(
        apiUrlProvider = apiUrlProvider,
        apiKey = apiKey,
        isCollectionEnabled = isCollectionEnabled,
        clock = wildAnalyticsLocator.get(),
        eventsRepository = wildAnalyticsLocator.get(),
        coroutineScopeFactory = wildAnalyticsLocator.get(),
        log = wildAnalyticsLocator.get(),
    )
    attributionStrategy.execute(
        context = wildAnalyticsLocator.get(),
        analytics = analytics,
        scopeFactory = wildAnalyticsLocator.get(),
    )
    return analytics
}

/**
 * @param isAttributionTrackingEnabled стратегия проверки атрибуции.
 * По умолчанию не проверяем.
 * */
public fun WildAnalytics(
    context: Context,
    apiUrlProvider: () -> ApiUrl = { DEFAULT_PROD_URL },
    apiKey: ApiKey,
    isCollectionEnabled: Boolean = true,
    isAttributionTrackingEnabled: Boolean = false,
    handleAttributionLink: suspend (link: String) -> Unit = {}
) {
    WildAnalytics(
        context = context,
        apiUrlProvider = apiUrlProvider,
        apiKey = apiKey,
        isCollectionEnabled = isCollectionEnabled,
        attributionStrategy = if (isAttributionTrackingEnabled) {
            AttributionStrategy.HandleLinkOnAttribution(handleLink = handleAttributionLink)
        } else {
            AttributionStrategy.NotCheckAttribution
        }
    )
}

internal fun <T> Flow<T>.rateLimit(delay: Duration): Flow<T> = channelFlow {
    var latest: T? = null
    var job: Job? = null

    fun emitAfterDelay() {
        if (job != null)
            return

        job = launch {
            delay(delay)
            send(latest!!)
            job = null
        }
    }

    launch {
        collect {
            latest = it
            emitAfterDelay()
        }
    }
}

internal fun Map<String, String>.toJsonObject(): JsonObject {
    val contentedMap = if (isEmpty()) {
        emptyMap()
    } else {
        mapValuesTo(hashMapOf()) { JsonPrimitive(it.value) }
    }
    return JsonObject(contentedMap)
}

internal val osBuild: String by lazy {
    Build.VERSION.RELEASE?.parseOsBuild()
        ?: getOsBuildFromVersionCode()
        ?: Build.VERSION.CODENAME?.parseOsBuild()
        ?: "" }

// Ожидаемый бэком формат версии ОС -- "00.00.00". Меньше составляющих распарсится (в недостающие запишется ноль), больше -- ошибка.
// https://youtrack.wildberries.ru/issue/ANDR-31076/WildAnalytics-SDK-Android-versiya-OS-v-batche
private fun String.parseOsBuild(): String? = split(".")
    .mapNotNull { part -> part.takeWhile { it.isDigit() }.takeLast(2).takeIf { it.isNotEmpty() } }
    .take(3)
    .takeIf { it.isNotEmpty() }
    ?.joinToString(separator = ".")

private fun getOsBuildFromVersionCode(): String? = when (Build.VERSION.SDK_INT) {
    VERSION_CODES.LOLLIPOP_MR1 -> "5.1"
    VERSION_CODES.M -> "6.0"
    VERSION_CODES.N -> "7.0"
    VERSION_CODES.N_MR1 -> "7.1"
    VERSION_CODES.O -> "8.0"
    VERSION_CODES.O_MR1 -> "8.1"
    VERSION_CODES.P -> "9"
    VERSION_CODES.Q -> "10"
    VERSION_CODES.R -> "11"
    VERSION_CODES.S -> "12"
    VERSION_CODES.S_V2 -> "12.1"
    VERSION_CODES.TIRAMISU -> "13"
    VERSION_CODES.UPSIDE_DOWN_CAKE -> "14"
    VERSION_CODES.VANILLA_ICE_CREAM -> "15"
    36 -> "16"
    else -> null
}