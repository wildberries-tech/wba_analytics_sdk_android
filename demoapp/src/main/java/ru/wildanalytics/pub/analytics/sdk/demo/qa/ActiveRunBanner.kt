package ru.wildanalytics.pub.analytics.sdk.demo.qa

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.core.formatDuration
import ru.wildanalytics.pub.analytics.sdk.demo.core.ui.glyph

/** Плашка идущего прогона над списком сценариев; тап открывает лог прогона. */
@Composable
fun ActiveRunBanner(title: String, verdict: Verdict?, elapsedSeconds: Long, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = "${verdict?.status.glyph} " + stringResource(R.string.qa_running, title, elapsedSeconds.formatDuration()) + " ›",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
