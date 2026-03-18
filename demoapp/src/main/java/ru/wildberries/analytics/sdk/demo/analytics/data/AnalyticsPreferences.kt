package ru.wildberries.analytics.sdk.demo.analytics.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.core.content.edit

class AnalyticsPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("analytics_demo_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveSdkConfig(data: AnalyticsSdkConfigData) {
        prefs.edit { putString(KEY_CONFIG, json.encodeToString(data)) }
    }

    fun loadSdkConfig(): AnalyticsSdkConfigData? {
        return prefs.getString(KEY_CONFIG, null)?.let {
            runCatching { json.decodeFromString<AnalyticsSdkConfigData>(it) }.getOrNull()
        }
    }

    fun saveEventConfig(data: AnalyticsEventConfigData) {
        prefs.edit { putString(KEY_EVENT, json.encodeToString(data)) }
    }

    fun loadEventConfig(): AnalyticsEventConfigData? {
        return prefs.getString(KEY_EVENT, null)?.let {
            runCatching { json.decodeFromString<AnalyticsEventConfigData>(it) }.getOrNull()
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val KEY_CONFIG = "config_state"
        private const val KEY_EVENT = "event_state"
    }
}
