package ru.wildanalytics.pub.analytics.sdk.demo.analytics.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.wildanalytics.pub.analytics.WildAnalyticsServiceLocator
import ru.wildanalytics.pub.analytics.config.WildAnalyticsConfig
import java.lang.reflect.Method

/**
 * Источник данных конфигурации SDK, доступ к которому осуществляется через рефлексию.
 *
 * Мы используем рефлексию здесь, чтобы обеспечить внутреннее тестирование и тонкую настройку
 * всех параметров SDK, сохраняя их недоступными для обычных клиентов. Это позволяет безопасно
 * экспериментировать с внутренними настройками перед их официальным открытием в публичном API.
 */
class ReflectionConfigDataSource(context: Context) {
    private val configRepository: Any?
    private val updateMethod: Method?
    val configFlow: StateFlow<WildAnalyticsConfig?>

    init {
        val result = runCatching {
            val locator = WildAnalyticsServiceLocator.getInstance(context)
            val repoClass = Class.forName("ru.wildanalytics.pub.analytics.config.ConfigRepository")

            val repo = locator.get(repoClass)
            val updateMethod = repoClass.getDeclaredMethod("updateConfig", Function1::class.java)
            val getConfigMethod = repoClass.getDeclaredMethod("getConfig")

            @Suppress("UNCHECKED_CAST")
            val sdkFlow = getConfigMethod.invoke(repo) as StateFlow<WildAnalyticsConfig>

            Triple(repo, updateMethod, sdkFlow)
        }.onFailure {
            Log.e("WildAnalytics", "Failed to initialize reflection config data source", it)
        }.getOrNull()

        configRepository = result?.first
        updateMethod = result?.second
        configFlow = result?.third ?: MutableStateFlow(null)
    }

    fun updateConfig(update: (WildAnalyticsConfig) -> WildAnalyticsConfig): Result<Unit> {
        val repo = configRepository
            ?: return Result.failure(IllegalStateException("ConfigRepository not initialized"))
        val method = updateMethod
            ?: return Result.failure(IllegalStateException("updateConfig method not found"))

        return runCatching {
            method.invoke(repo, update)
            Unit
        }
    }
}
