package ru.wildberries.pub.analytics.sdk.demo.sdk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.wildberries.pub.analytics.WBAnalytics2ServiceLocator
import ru.wildberries.pub.analytics.config.WBA2Config
import java.lang.reflect.Method

/** Доступ к внутреннему ConfigRepository SDK через рефлексию: тонкая настройка вне публичного API. */
class ReflectionConfigDataSource(context: Context) {
    private val configRepository: Any?
    private val updateMethod: Method?
    val configFlow: StateFlow<WBA2Config?>

    init {
        val result = runCatching {
            val locator = WBAnalytics2ServiceLocator.getInstance(context)
            val repoClass = Class.forName("ru.wildberries.pub.analytics.config.ConfigRepository")

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
        }
    }
}
