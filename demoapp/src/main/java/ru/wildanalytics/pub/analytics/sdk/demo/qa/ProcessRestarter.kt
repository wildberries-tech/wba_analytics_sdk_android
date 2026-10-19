package ru.wildanalytics.pub.analytics.sdk.demo.qa

import android.content.Context
import android.content.Intent
import ru.wildanalytics.pub.analytics.sdk.demo.app.AppActivity

/** Перезапуск процесса с продолжением прогона: SDK держит часть состояния только в памяти. */
class ProcessRestarter(
    private val context: Context,
    private val pending: PendingRunStore,
) {

    fun restart(run: PendingRun) {
        pending.save(run)
        val intent = Intent(context, AppActivity::class.java)
        context.startActivity(Intent.makeRestartActivityTask(intent.component))
        Runtime.getRuntime().exit(0)
    }
}
