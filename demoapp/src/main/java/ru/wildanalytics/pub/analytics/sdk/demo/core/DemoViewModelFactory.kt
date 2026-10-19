package ru.wildanalytics.pub.analytics.sdk.demo.core

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.wildanalytics.pub.analytics.sdk.demo.app.AppContainer
import ru.wildanalytics.pub.analytics.sdk.demo.app.DemoApplication

/** ViewModel экрана с зависимостями из [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> demoViewModel(crossinline create: (AppContainer) -> VM): VM =
    viewModel(
        factory = viewModelFactory {
            initializer { create((requireNotNull(this[APPLICATION_KEY]) as DemoApplication).container) }
        }
    )
