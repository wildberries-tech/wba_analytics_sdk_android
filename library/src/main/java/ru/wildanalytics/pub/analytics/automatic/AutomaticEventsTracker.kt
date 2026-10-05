package ru.wildanalytics.pub.analytics.automatic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.wildanalytics.pub.analytics.WildAnalytics
import ru.wildanalytics.pub.analytics.device.DeviceHardwareInfoProvider
import ru.wildanalytics.pub.analytics.device.WildDeviceInfoProvider
import ru.wildanalytics.pub.analytics.util.ProcessForegroundDataProvider

internal interface AutomaticEventsTracker {
    fun start(wba: WildAnalytics, scope: CoroutineScope)
    fun stop()

    fun interface Factory {
        fun create(): AutomaticEventsTracker
    }
}

internal class AutomaticEventsTrackerImpl(
    private val processForegroundDataProvider: ProcessForegroundDataProvider,
    private val deviceHardwareInfoProvider: DeviceHardwareInfoProvider,
    private val deviceInfoProvider: WildDeviceInfoProvider,
) : AutomaticEventsTracker {

    private var heartbeatJob: Job? = null

    override fun start(wba: WildAnalytics, scope: CoroutineScope) {
        logFirstOpenIfNeeded(wba)
        logApplicationStart(scope, wba)
        logHeartbeat(scope, wba)
    }

    private fun logHeartbeat(scope: CoroutineScope, wba: WildAnalytics) {
        processForegroundDataProvider.processForegroundData
            .distinctUntilChangedBy { it.isInForeground }
            .onEach {
                heartbeatJob?.cancel()
                if (it.isInForeground) {
                    heartbeatJob = scope.launch {
                        while (isActive) {
                            delay(HEARTBEAT_INTERVAL_MS)
                            wba.logEvent(EVENT_HEARTBEAT)
                        }
                    }
                }
            }
            .launchIn(scope)
    }

    override fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun logFirstOpenIfNeeded(wba: WildAnalytics) {
        if (deviceInfoProvider.isUserNew()) {
            wba.logEvent(EVENT_FIRST_OPEN)
        }
    }

    private fun logApplicationStart(scope: CoroutineScope, wba: WildAnalytics) {
        var isForegroundStartLocationLogged = false
        processForegroundDataProvider.processForegroundData
            .filter { it.isInForeground }
            .onEach {
                val startLocation = if (isForegroundStartLocationLogged) {
                    START_LOCATION_BACKGROUND
                } else {
                    isForegroundStartLocationLogged = true
                    START_LOCATION_FOREGROUND
                }
                wba.logApplicationStart(startLocation)
            }
            .launchIn(scope)
    }

    private fun WildAnalytics.logApplicationStart(startLocation: String) {
        val params = buildJsonObject {
            put(PARAM_START_LOCATION, startLocation)
            put(PARAM_CPU, deviceHardwareInfoProvider.cpuFreqGhz)
            put(PARAM_RAM, deviceHardwareInfoProvider.totalRamGb)
            deviceHardwareInfoProvider.processorName?.let { put(PARAM_PROCESSOR_NAME, it) }
        }

        logEvent(EVENT_APPLICATION_START, params)
    }

    companion object {
        private const val EVENT_FIRST_OPEN = "first_open"
        private const val EVENT_APPLICATION_START = "application_start"
        private const val EVENT_HEARTBEAT = "heartbeat"

        private const val PARAM_START_LOCATION = "start_location"
        private const val PARAM_CPU = "cpu"
        private const val PARAM_RAM = "ram"
        private const val PARAM_PROCESSOR_NAME = "processor_name"

        private const val START_LOCATION_FOREGROUND = "foreground"
        private const val START_LOCATION_BACKGROUND = "background"

        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }
}
