package ru.wildanalytics.pub.analytics.sdk.demo.qa.loss

import androidx.annotation.StringRes
import ru.wildanalytics.pub.analytics.sdk.demo.R

enum class LossScenario(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:StringRes val expected: Int,
) {
    EVENTS_KEPT_ON_FAILURE(
        title = R.string.qa_loss_kept_title,
        description = R.string.qa_loss_kept_description,
        expected = R.string.qa_loss_kept_expected,
    ),
    QUEUE_EVICTION(
        title = R.string.qa_loss_eviction_title,
        description = R.string.qa_loss_eviction_description,
        expected = R.string.qa_loss_eviction_expected,
    ),
    SEND_LOOP_RESTART(
        title = R.string.qa_loss_restart_title,
        description = R.string.qa_loss_restart_description,
        expected = R.string.qa_loss_restart_expected,
    ),
    INSERT_FAILED(
        title = R.string.qa_loss_insert_title,
        description = R.string.qa_loss_insert_description,
        expected = R.string.qa_loss_insert_expected,
    ),
}
