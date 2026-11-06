package ru.wildberries.pub.WBAnalytics2

import android.content.Context
import androidx.startup.Initializer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.wildberries.pub.analytics.WBAnalytics2ServiceLocator
import ru.wildberries.pub.analytics.send.WBAnalytics2SenderService

@Suppress("unused")
public class FeatureInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val sl = WBAnalytics2ServiceLocator.getInstance(context)
        @OptIn(DelicateCoroutinesApi::class)
        @SuppressWarnings("GlobalCoroutineUsage")
        GlobalScope.launch(Dispatchers.Default) {
            // Инициализируем весь DI граф на отдельном потоке, чтобы не нагружать мейн тред.
            sl.get<WBAnalytics2SenderService>() // Запускает сервис.
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
