package ru.wildanalytics.pub.analytics.sdk.demo.sdk

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AnalyticsPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("analytics_demo_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveSdkConfig(data: AnalyticsSdkConfigData) {
        prefs.edit(commit = true) { putString(KEY_CONFIG, json.encodeToString(data)) }
    }

    fun loadSdkConfig(): AnalyticsSdkConfigData? {
        return prefs.getString(KEY_CONFIG, null)?.let {
            runCatching { json.decodeFromString<AnalyticsSdkConfigData>(it) }.getOrNull()
        }
    }

    fun saveEventConfig(data: AnalyticsEventConfigData) {
        prefs.edit(commit = true) { putString(KEY_EVENT, json.encodeToString(data)) }
    }

    fun loadEventConfig(): AnalyticsEventConfigData? {
        return prefs.getString(KEY_EVENT, null)?.let {
            runCatching { json.decodeFromString<AnalyticsEventConfigData>(it) }.getOrNull()
        }
    }

    fun saveEndpointConfig(data: AnalyticsEndpointConfigData) {
        prefs.edit(commit = true) { putString(KEY_ENDPOINT, json.encodeToString(data)) }
    }

    fun loadEndpointConfig(): AnalyticsEndpointConfigData? {
        return prefs.getString(KEY_ENDPOINT, null)?.let {
            runCatching { json.decodeFromString<AnalyticsEndpointConfigData>(it) }.getOrNull()
        }
    }

    fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    companion object {
        private const val KEY_CONFIG = "config_state"
        private const val KEY_EVENT = "event_state"
        private const val KEY_ENDPOINT = "endpoint_state"
    }
}
