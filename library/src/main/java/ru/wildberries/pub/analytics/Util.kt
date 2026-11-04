package ru.wildberries.pub.analytics

import android.content.Context
import android.os.Build
import android.os.Build.VERSION_CODES
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.wildberries.pub.analytics.automatic.AutomaticEventsStrategy
import ru.wildberries.pub.analytics.send.fallback.EndpointFallback
import ru.wildberries.pub.attribution.api.AttributionStrategy
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
public var wbAnalytics2CustomLogger: WBAnalytics2Logger? = null

internal const val DEFAULT_PROD_URL = "https://a.wb.ru/m/batch"
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
 * [WBAnalytics2.setCommonParameter] / [WBAnalytics2.setCommonParameters] (значение `null` тоже считается),
 * но не дольше [REQUIRED_COMMON_PARAMETERS_TIMEOUT]. Ожидание одноразовое: после открытия
 * события обрабатываются без задержки.
 * @param fallbackApiUrl резервный URL сервиса аналитики (полный, https).
 * Если 3 попытки отправки батча подряд не прошли из-за сети
 * или HTTP 5xx при работающем интернете, отправка переключается на резервный URL до перезапуска процесса.
 * `null` или пустая строка не задают резервный URL; невалидный или не https URL игнорируется с предупреждением в лог.
 * */
public fun WBAnalytics2(
    context: Context,
    apiUrlProvider: () -> ApiUrl = { DEFAULT_PROD_URL },
    apiKey: ApiKey,
    enableAutomaticEvents: Boolean,
    isCollectionEnabled: Boolean = true,
    attributionStrategy: AttributionStrategy = AttributionStrategy.NotCheckAttribution,
    requiredCommonParameters: Set<String> = emptySet(),
    fallbackApiUrl: ApiUrl? = null,
): WBAnalytics2 {
    require(apiKey.isNotEmpty())
    require(requiredCommonParameters.all { it.length in 1..MAX_COMMON_PARAMETER_KEY_LENGTH })
    val wbAnalytics2Locator = WBAnalytics2ServiceLocator.getInstance(context)
    if (!wbAnalytics2Locator.get<EndpointFallback>().register(apiKey, fallbackApiUrl)) {
        wbAnalytics2Locator.get<WBAnalytics2Logger>().logWarn("fallbackApiUrl=$fallbackApiUrl is ignored, must be a valid https URL")
    }
    val automaticEventsStrategy = if (enableAutomaticEvents) {
        AutomaticEventsStrategy.Track(
            apiKey = apiKey,
            registry = wbAnalytics2Locator.get(),
            trackerFactory = wbAnalytics2Locator.get(),
        )
    } else {
        AutomaticEventsStrategy.DoNotTrack
    }
    val wba = WBAnalytics2Impl(
        apiUrlProvider = apiUrlProvider,
        apiKey = apiKey,
        isCollectionEnabled = isCollectionEnabled,
        clock = wbAnalytics2Locator.get(),
        eventsRepository = wbAnalytics2Locator.get(),
        sessionProvider = wbAnalytics2Locator.get(),
        coroutineScopeFactory = wbAnalytics2Locator.get(),
        log = wbAnalytics2Locator.get(),
        customHeadersRepository = wbAnalytics2Locator.get(),
        enricherRegistry = wbAnalytics2Locator.get(),
        automaticEventsStrategy = automaticEventsStrategy,
        commonParametersGate = CommonParametersGate(
            requiredKeys = requiredCommonParameters,
            timeout = REQUIRED_COMMON_PARAMETERS_TIMEOUT,
            log = wbAnalytics2Locator.get(),
        ),
        diagnostics = wbAnalytics2Locator.get(),
    )
    attributionStrategy.execute(
        context = wbAnalytics2Locator.get(),
        wba = wba,
        scopeFactory = wbAnalytics2Locator.get(),
    )
    return wba
}

/**
 * @param attributionStrategy стратегия проверки атрибуции.
 * По умолчанию не проверяем.
 *
 * Выделен в отдельный метод для обратной совместимости. Например, для WBID.
 * */
public fun WBAnalytics2(
    context: Context,
    apiUrlProvider: () -> ApiUrl = { DEFAULT_PROD_URL },
    apiKey: ApiKey,
    isCollectionEnabled: Boolean = true,
    attributionStrategy: AttributionStrategy = AttributionStrategy.NotCheckAttribution,
): WBAnalytics2 = WBAnalytics2(
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
public fun WBAnalytics2(
    context: Context,
    apiUrlProvider: () -> ApiUrl = { DEFAULT_PROD_URL },
    apiKey: ApiKey,
    isCollectionEnabled: Boolean = true,
    isAttributionTrackingEnabled: Boolean = false,
    enableAutomaticEvents: Boolean = false,
    handleAttributionLink: suspend (link: String) -> Unit,
) {
    WBAnalytics2(
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
// https://youtrack.wildberries.ru/issue/ANDR-31076/WBA-SDK-Android-versiya-OS-v-batche
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