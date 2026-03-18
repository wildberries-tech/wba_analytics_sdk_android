package ru.wildberries.analytics.sdk.demo.analytics.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.wildberries.analytics.WBAnalytics2ServiceLocator
import ru.wildberries.analytics.config.WBA2Config
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
    val configFlow: StateFlow<WBA2Config?>

    init {
        val result = runCatching {
            val locator = WBAnalytics2ServiceLocator.getInstance(context)
            val repoClass = Class.forName("ru.wildberries.analytics.config.ConfigRepository")

            val repo = locator.get(repoClass)
            val updateMethod = repoClass.getDeclaredMethod("updateConfig", Function1::class.java)
            val getConfigMethod = repoClass.getDeclaredMethod("getConfig")

            @Suppress("UNCHECKED_CAST")
            val sdkFlow = getConfigMethod.invoke(repo) as StateFlow<WBA2Config>

            Triple(repo, updateMethod, sdkFlow)
        }.onFailure {
            Log.e("WBA2", "Failed to initialize reflection config data source", it)
        }.getOrNull()

        configRepository = result?.first
        updateMethod = result?.second
        configFlow = result?.third ?: MutableStateFlow(null)
    }

    fun updateConfig(update: (WBA2Config) -> WBA2Config): Result<Unit> {
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
