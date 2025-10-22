package ru.wildberries.analytics

import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.wildberries.attribution.api.AttributionStrategy
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
public var wbAnalytics2CustomLogger: WBAnalytics2Logger? = null

internal const val DEFAULT_PROD_URL = "https://wba.wb.ru/m/batch"

/**
 * @param attributionStrategy стратегия проверки атрибуции.
 * По умолчанию не проверяем.
 * */
public fun WBAnalytics2(
    context: Context,
    apiUrlProvider: () -> ApiUrl = { DEFAULT_PROD_URL },
    apiKey: ApiKey,
    isCollectionEnabled: Boolean = true,
    attributionStrategy: AttributionStrategy = AttributionStrategy.NotCheckAttribution,
): WBAnalytics2 {
    require(apiKey.isNotEmpty())
    val wbAnalytics2Locator = WBAnalytics2ServiceLocator.getInstance(context)
    val wba = WBAnalytics2Impl(
        apiUrlProvider = apiUrlProvider,
        apiKey = apiKey,
        isCollectionEnabled = isCollectionEnabled,
        clock = wbAnalytics2Locator.get(),
        eventsRepository = wbAnalytics2Locator.get(),
        coroutineScopeFactory = wbAnalytics2Locator.get(),
        log = wbAnalytics2Locator.get(),
    )
    attributionStrategy.execute(
        context = wbAnalytics2Locator.get(),
        wba = wba,
        scopeFactory = wbAnalytics2Locator.get(),
    )
    return wba
}

/**
 * @param isAttributionTrackingEnabled стратегия проверки атрибуции.
 * По умолчанию не проверяем.
 * */
public fun WBAnalytics2(
    context: Context,
    apiUrlProvider: () -> ApiUrl = { DEFAULT_PROD_URL },
    apiKey: ApiKey,
    isCollectionEnabled: Boolean = true,
    isAttributionTrackingEnabled: Boolean = false,
    handleAttributionLink: suspend (link: String) -> Unit = {}
) {
    WBAnalytics2(
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
