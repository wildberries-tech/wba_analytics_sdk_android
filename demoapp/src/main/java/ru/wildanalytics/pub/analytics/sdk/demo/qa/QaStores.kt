package ru.wildanalytics.pub.analytics.sdk.demo.qa

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface KeyValueStore {
    fun get(key: String): String?
    fun all(): Map<String, String>
    fun put(key: String, value: String?)
    fun clear()
}

class PrefsKeyValueStore(context: Context, name: String) : KeyValueStore {

    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun all(): Map<String, String> = prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    override fun put(key: String, value: String?) {
        prefs.edit(commit = true) { if (value == null) remove(key) else putString(key, value) }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }
}

/** Итог последнего прогона каждого сценария; переживает перезапуск процесса. */
class ResultStore(private val store: KeyValueStore) {

    private val _results = MutableStateFlow(read())
    val results: StateFlow<Map<ResultKey, ScenarioResult>> = _results.asStateFlow()

    fun save(suite: QaSuite, scenario: String, result: ScenarioResult) {
        store.put("${suite.name}$SEPARATOR$scenario", "${result.status.name}$SEPARATOR${result.elapsedSeconds}")
        _results.value = read()
    }

    fun clear(suite: QaSuite) {
        store.all().keys.filter { it.startsWith(suite.name + SEPARATOR) }.forEach { store.put(it, null) }
        _results.value = read()
    }

    private fun read(): Map<ResultKey, ScenarioResult> = store.all().mapNotNull { (key, value) ->
        val suite = QaSuite.entries.firstOrNull { it.name == key.substringBefore(SEPARATOR) } ?: return@mapNotNull null
        val status = Verdict.Status.entries.firstOrNull { it.name == value.substringBefore(SEPARATOR) }
        val seconds = value.substringAfter(SEPARATOR, "").toLongOrNull()
        if (status == null || seconds == null) return@mapNotNull null
        ResultKey(suite, key.substringAfter(SEPARATOR)) to ScenarioResult(status, seconds)
    }.toMap()
}

/** Прогон, который нужно продолжить после перезапуска процесса. */
class PendingRunStore(private val store: KeyValueStore) {

    fun save(run: PendingRun) {
        store.put(KEY_PENDING, "${run.suite.name}$SEPARATOR${run.scenario.orEmpty()}")
    }

    fun take(): PendingRun? {
        val value = store.get(KEY_PENDING) ?: return null
        store.put(KEY_PENDING, null)
        val suite = QaSuite.entries.firstOrNull { it.name == value.substringBefore(SEPARATOR) } ?: return null
        return PendingRun(suite, value.substringAfter(SEPARATOR, "").ifEmpty { null })
    }
}

private const val SEPARATOR = ":"
private const val KEY_PENDING = "pending_run"
