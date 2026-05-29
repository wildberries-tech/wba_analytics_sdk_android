package ru.wildanalytics.pub.analytics.sdk.demo.analytics.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class AnalyticsEventConfig(
    val counterId: Long,
    val name: String,
    val value: String,
    val count: Int,
    val isImportant: Boolean,
    val commonParameters: Map<String, String>
){
    val apiKey: String = counterId.toApiKey()
}

@OptIn(ExperimentalEncodingApi::class)
private fun Long.toApiKey(): String {
    val binary = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(this)
        .array()
    return Base64.encode(binary)
}