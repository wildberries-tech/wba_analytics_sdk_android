package ru.wildanalytics.pub.analytics.sdk.demo.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.wildanalytics.pub.analytics.sdk.demo.R
import ru.wildanalytics.pub.analytics.sdk.demo.qa.QaSuite
import ru.wildanalytics.pub.analytics.sdk.demo.qa.Verdict

@Composable
fun ScenarioRow(
    number: Int,
    title: String,
    status: Verdict.Status?,
    onRun: () -> Unit,
    precondition: String? = null,
    details: @Composable () -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = status.glyph,
                color = status.color(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(GLYPH_WIDTH.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "$number  $title", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                precondition?.let {
                    Text(text = "⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onRun) { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.qa_run)) }
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = GLYPH_WIDTH.dp, end = 12.dp, bottom = 8.dp)) { details() }
        }
    }
}

@Composable
fun LogItemRow(
    prefix: String,
    glyph: String,
    text: String,
    attributes: Map<String, String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    glyphColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = attributes.isNotEmpty(), onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MonoText(prefix, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MonoText(glyph, color = glyphColor)
            MonoText(text + if (attributes.isNotEmpty() && !expanded) " ▸" else "")
        }
        if (expanded) {
            attributes.forEach { (key, value) ->
                MonoText("$key=$value", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp))
            }
        }
    }
}

@Composable
fun FilterChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            FilterChip(selected = index == selected, onClick = { onSelect(index) }, label = { Text(text = label) })
        }
    }
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = color, modifier = modifier)
}

@Composable
fun Verdict.Status?.color(): Color = when (this) {
    Verdict.Status.PASSED -> PASSED_COLOR
    Verdict.Status.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

val Verdict.Status?.glyph: String
    get() = when (this) {
        Verdict.Status.PASSED -> "✓"
        Verdict.Status.FAILED -> "✗"
        Verdict.Status.WAITING -> "⏳"
        null -> "·"
    }

fun scenarioStatus(isActive: Boolean, verdict: Verdict?, saved: Verdict.Status?): Verdict.Status? =
    if (isActive) verdict?.status ?: Verdict.Status.WAITING else saved

fun Verdict.Status.label(): Int = when (this) {
    Verdict.Status.WAITING -> R.string.qa_status_waiting
    Verdict.Status.PASSED -> R.string.qa_status_passed
    Verdict.Status.FAILED -> R.string.qa_status_failed
}

val PASSED_COLOR = Color(0xFF2E7D32)
val PASSED_CONTAINER = Color(0xFFDDF2E0)
val PASSED_CONTENT = Color(0xFF0F3D1A)
private const val GLYPH_WIDTH = 24

val QaSuite.title: Int
    get() = when (this) {
        QaSuite.FALLBACK -> R.string.qa_suite_fallback
        QaSuite.LOSS -> R.string.qa_suite_loss
    }
