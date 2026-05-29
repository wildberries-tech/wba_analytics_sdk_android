package ru.wildanalytics.pub.analytics.transport

import okhttp3.OkHttpClient.Builder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @property connectTimeout - see [Builder.connectTimeout]
 * @property readTimeout - see [Builder.readTimeout]
 * @property writeTimeout - see [Builder.writeTimeout]
 * */
public data class HttpTimeouts(
    val connectTimeout: Duration= 40.seconds,
    val readTimeout: Duration= 5.seconds,
    val writeTimeout: Duration= 15.seconds,
)
