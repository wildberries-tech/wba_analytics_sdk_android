package ru.wildanalytics.pub.analytics.sdk.demo.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTime(timestampMillis: Long): String = SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(timestampMillis))

fun Long.formatDuration(): String = "%d:%02d".format(Locale.US, this / SECONDS_IN_MINUTE, this % SECONDS_IN_MINUTE)

private const val TIME_PATTERN = "HH:mm:ss"
private const val SECONDS_IN_MINUTE = 60
