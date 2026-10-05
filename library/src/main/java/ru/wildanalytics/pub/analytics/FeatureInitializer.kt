package ru.wildanalytics.pub.analytics

import android.content.Context
import androidx.startup.Initializer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.wildanalytics.pub.analytics.send.WildAnalyticsSenderService

@Suppress("unused", "GlobalCoroutineUsage")
public class FeatureInitializer : Initializer<Unit> {

    @OptIn(DelicateCoroutinesApi::class)
    override fun create(context: Context) {
        val sl = WildAnalyticsServiceLocator.getInstance(context)
        GlobalScope.launch(Dispatchers.Default) {
            // Инициализируем весь DI граф на отдельном потоке, чтобы не нагружать мейн тред.
            sl.get<WildAnalyticsSenderService>() // Запускает сервис.
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
