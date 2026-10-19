package ru.wildanalytics.pub.analytics.sdk.demo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.combine
import ru.wildanalytics.pub.analytics.sdk.demo.config.SdkConfigScreen
import ru.wildanalytics.pub.analytics.sdk.demo.config.SdkConfigViewModel
import ru.wildanalytics.pub.analytics.sdk.demo.core.demoViewModel
import ru.wildanalytics.pub.analytics.sdk.demo.diagnostics.DiagnosticsScreen
import ru.wildanalytics.pub.analytics.sdk.demo.diagnostics.DiagnosticsViewModel
import ru.wildanalytics.pub.analytics.sdk.demo.endpoint.EndpointScreen
import ru.wildanalytics.pub.analytics.sdk.demo.endpoint.EndpointViewModel
import ru.wildanalytics.pub.analytics.sdk.demo.events.EventsScreen
import ru.wildanalytics.pub.analytics.sdk.demo.events.EventsViewModel
import ru.wildanalytics.pub.analytics.sdk.demo.home.HomeActions
import ru.wildanalytics.pub.analytics.sdk.demo.home.HomeScreen
import ru.wildanalytics.pub.analytics.sdk.demo.home.HomeViewModel
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaHubScreen
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaHubViewModel
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.RunLogScreen
import ru.wildanalytics.pub.analytics.sdk.demo.qa.RunLogViewModel
import ru.wildanalytics.pub.analytics.sdk.demo.qa.scenarioTitle
import ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback.FallbackQaScreen
import ru.wildanalytics.pub.analytics.sdk.demo.qa.fallback.FallbackQaViewModel
import ru.wildanalytics.pub.analytics.sdk.demo.qa.loss.LossQaScreen
import ru.wildanalytics.pub.analytics.sdk.demo.qa.loss.LossQaViewModel

@Composable
fun DemoNavHost(navController: NavHostController, resumedSuite: QaSuite?) {
    val back: () -> Unit = { navController.popBackStack() }
    LaunchedEffect(resumedSuite) {
        if (resumedSuite != null) {
            navController.navigate(QaHubRoute)
            navController.navigate(resumedSuite.route)
            navController.navigate(RunLogRoute(resumedSuite))
        }
    }
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            val viewModel = demoViewModel { c ->
                HomeViewModel(
                    repository = c.repository,
                    queueStats = c.queueStats,
                    entries = c.logger.entries,
                    requests = c.requestLog.requests,
                    results = c.results.results,
                    periodicSeconds = c.periodicLogger.intervalSeconds,
                    activeRuns = combine(c.fallback.tracker.active, c.loss.tracker.active) { a, b -> listOfNotNull(a, b) },
                    suiteSizes = c.suiteSizes,
                )
            }
            HomeScreen(
                viewModel = viewModel,
                actions = HomeActions(
                    onEvents = { navController.navigate(EventsRoute) },
                    onConfig = { navController.navigate(SdkConfigRoute) },
                    onEndpoint = { navController.navigate(EndpointRoute) },
                    onDiagnostics = { navController.navigate(DiagnosticsRoute) },
                    onQa = { navController.navigate(QaHubRoute) },
                ),
            )
        }
        composable<EventsRoute> { EventsScreen(demoViewModel { EventsViewModel(it.repository) }, back) }
        composable<SdkConfigRoute> { SdkConfigScreen(demoViewModel { SdkConfigViewModel(it.repository) }, back) }
        composable<EndpointRoute> { EndpointScreen(demoViewModel { EndpointViewModel(it.repository, it.logger.entries) }, back) }
        composable<DiagnosticsRoute> {
            DiagnosticsScreen(demoViewModel { c -> DiagnosticsViewModel(c.logger.entries, c.logger::clear) }, back)
        }
        composable<QaHubRoute> {
            val viewModel = demoViewModel { c ->
                QaHubViewModel(
                c.results.results,
                listOf(c.fallback.tracker, c.loss.tracker),
                c.suiteSizes
            )
            }
            QaHubScreen(viewModel, back) { suite -> navController.navigate(suite.route) }
        }
        composable<FallbackQaRoute> {
            val viewModel = demoViewModel { c -> FallbackQaViewModel(c.fallback, c.results.results) { c.results.clear(QaSuite.FALLBACK) } }
            FallbackQaScreen(viewModel, back) { navController.navigate(RunLogRoute(QaSuite.FALLBACK)) }
        }
        composable<LossQaRoute> {
            val viewModel = demoViewModel { c ->
                LossQaViewModel(c.loss, c.repository, c.queueStats, c.results.results) { c.results.clear(QaSuite.LOSS) }
            }
            LossQaScreen(viewModel, back) { navController.navigate(RunLogRoute(QaSuite.LOSS)) }
        }
        composable<RunLogRoute> { entry ->
            val suite = entry.toRoute<RunLogRoute>().suite
            val viewModel = demoViewModel { c ->
                RunLogViewModel(
                c.tracker(suite),
                c.logger.entries,
                c.requestLog.requests,
                c.runActions(suite)
            )
            }
            RunLogScreen(viewModel, suite, titleOf = { name -> suite.scenarioTitle(name) }, onBack = back)
        }
    }
}

private val QaSuite.route: Any
    get() = when (this) {
        QaSuite.FALLBACK -> FallbackQaRoute
        QaSuite.LOSS -> LossQaRoute
    }
