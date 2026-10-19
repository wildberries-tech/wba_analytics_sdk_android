package ru.wildanalytics.pub.analytics.sdk.demo.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import ru.wildanalytics.pub.analytics.sdk.demo.R

fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
}

private const val CLIP_LABEL = "wba_demo"
