package ru.wildanalytics.pub.analytics

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.startup.Initializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.wildanalytics.pub.analytics.send.WildAnalyticsSenderService

@Suppress("unused")
public class FeatureInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val sl = WildAnalyticsServiceLocator.getInstance(context)
        ProcessLifecycleOwner.get().lifecycle.coroutineScope.launch(Dispatchers.Default) {
            // Инициализируем весь DI граф на отдельном потоке, чтобы не нагружать мейн тред.
            sl.get<WildAnalyticsSenderService>() // Запускает сервис.
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
