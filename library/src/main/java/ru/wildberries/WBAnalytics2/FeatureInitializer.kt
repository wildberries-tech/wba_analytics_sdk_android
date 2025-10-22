package ru.wildberries.WBAnalytics2

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.startup.Initializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.wildberries.analytics.WBAnalytics2ServiceLocator
import ru.wildberries.analytics.send.WBAnalytics2SenderService

@Suppress("unused")
public class FeatureInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val sl = WBAnalytics2ServiceLocator.getInstance(context)
        ProcessLifecycleOwner.get().lifecycle.coroutineScope.launch(Dispatchers.Default) {
            // Инициализируем весь DI граф на отдельном потоке, чтобы не нагружать мейн тред.
            sl.get<WBAnalytics2SenderService>() // Запускает сервис.
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
