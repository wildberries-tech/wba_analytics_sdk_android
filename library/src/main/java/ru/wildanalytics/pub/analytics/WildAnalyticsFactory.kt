package ru.wildanalytics.pub.analytics

import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.wildanalytics.pub.analytics.automatic.AutomaticEventsStrategy
import ru.wildanalytics.pub.attribution.api.AttributionStrategy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
internal const val MAX_COMMON_PARAMETER_KEY_LENGTH = 40
public val REQUIRED_COMMON_PARAMETERS_TIMEOUT: Duration = 5.seconds

/**
 * @param attributionStrategy стратегия проверки атрибуции.
 * По умолчанию не проверяем.
 * @param enableAutomaticEvents
 * включите (true) для автоматического отслеживания установок, запусков приложения и времени активности.
 * Если ваш продукт — библиотека для интеграции, держите false.
 * @param requiredCommonParameters ключи общих параметров, без которых события не должны уходить.
 * Обработка событий (включая автоматические) придерживается, пока каждый ключ не будет присвоен через
 * [WildAnalytics.setCommonParameter] / [WildAnalytics.setCommonParameters] (значение `null` тоже считается),
 * но не дольше [REQUIRED_COMMON_PARAMETERS_TIMEOUT]. Ожидание одноразовое: после открытия
 * события обрабатываются без задержки.
 * */
public fun WildAnalytics(
    context: Context,
    apiUrlProvider: () -> ApiUrl = { DEFAULT_PROD_URL },
    apiKey: ApiKey,
    enableAutomaticEvents: Boolean,
    isCollectionEnabled: Boolean = true,
    attributionStrategy: AttributionStrategy = AttributionStrategy.NotCheckAttribution,
    requiredCommonParameters: Set<String> = emptySet(),
    fallbackApiUrl: ApiUrl? = null,
): WildAnalytics {
    require(apiKey.isNotEmpty())
    require(requiredCommonParameters.all { it.length in 1..MAX_COMMON_PARAMETER_KEY_LENGTH })
    val wildAnalyticsLocator = WildAnalyticsServiceLocator.getInstance(context)
    if (!wildAnalyticsLocator.get<ru.wildanalytics.pub.analytics.send.fallback.EndpointFallback>().register(apiKey, fallbackApiUrl)) {
        wildAnalyticsLocator.get<WildAnalyticsLogger>().logWarn("fallbackApiUrl=$fallbackApiUrl is ignored, must be a valid https URL")
    }
    val automaticEventsStrategy = if (enableAutomaticEvents) {
        AutomaticEventsStrategy.Track(
            apiKey = apiKey,
            registry = wildAnalyticsLocator.get(),
            trackerFactory = wildAnalyticsLocator.get(),
        )
    } else {
        AutomaticEventsStrategy.DoNotTrack
    }
    val analytics = WildAnalyticsImpl(
        apiUrlProvider = apiUrlProvider,
        apiKey = apiKey,
        isCollectionEnabled = isCollectionEnabled,
        clock = wildAnalyticsLocator.get(),
        eventsRepository = wildAnalyticsLocator.get(),
        sessionProvider = wildAnalyticsLocator.get(),
        log = wildAnalyticsLocator.get(),
        coroutineScopeFactory = wildAnalyticsLocator.get(),
        customHeadersRepository = wildAnalyticsLocator.get(),
        enricherRegistry = wildAnalyticsLocator.get(),
        automaticEventsStrategy = automaticEventsStrategy,
        commonParametersGate = CommonParametersGate(
            requiredKeys = requiredCommonParameters,
            timeout = REQUIRED_COMMON_PARAMETERS_TIMEOUT,
            log = wildAnalyticsLocator.get(),
        ),
        diagnostics = wildAnalyticsLocator.get<ru.wildanalytics.pub.analytics.diagnostics.DiagnosticsReporter>(),
    )
    attributionStrategy.execute(
        context = wildAnalyticsLocator.get(),
        analytics = analytics,
        scopeFactory = wildAnalyticsLocator.get(),
    )
    return analytics
}

/**
 * @param attributionStrategy стратегия проверки атрибуции.
 * По умолчанию не проверяем.
 *
 * Выделен в отдельный метод для обратной совместимости.
 * */
public fun WildAnalytics(
    context: Context,
    apiUrlProvider: () -> ApiUrl = { DEFAULT_PROD_URL },
    apiKey: ApiKey,
    isCollectionEnabled: Boolean = true,
    attributionStrategy: AttributionStrategy = AttributionStrategy.NotCheckAttribution,
): WildAnalytics = WildAnalytics(
    context = context,
    apiUrlProvider = apiUrlProvider,
    apiKey = apiKey,
    enableAutomaticEvents = false,
    isCollectionEnabled = isCollectionEnabled,
    attributionStrategy = attributionStrategy,
)

/**
 * @param isAttributionTrackingEnabled стратегия проверки атрибуции.
 * По умолчанию не проверяем.
 * @param enableAutomaticEvents
 * включите (true) для автоматического отслеживания установок, запусков приложения и времени активности.
 * Если ваш продукт — библиотека для интеграции, держите false.
 * */
@Suppress("FunctionNaming")
public fun WildAnalytics(
    context: Context,
    apiUrlProvider: () -> ApiUrl = { DEFAULT_PROD_URL },
    apiKey: ApiKey,
    isCollectionEnabled: Boolean = true,
    isAttributionTrackingEnabled: Boolean = false,
    enableAutomaticEvents: Boolean = false,
    handleAttributionLink: suspend (link: String) -> Unit = {},
) {
    WildAnalytics(
        context = context,
        apiUrlProvider = apiUrlProvider,
        apiKey = apiKey,
        isCollectionEnabled = isCollectionEnabled,
        enableAutomaticEvents = enableAutomaticEvents,
        attributionStrategy = if (isAttributionTrackingEnabled) {
            AttributionStrategy.HandleLinkOnAttribution(handleLink = handleAttributionLink)
        } else {
            AttributionStrategy.NotCheckAttribution
        },
    )
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
        ?: ""
}

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
