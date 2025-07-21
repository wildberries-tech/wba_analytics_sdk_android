package ru.wildberries.analytics.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink

internal class JsonBody<T>(
    private val value: T,
    private val serializer: SerializationStrategy<T>
) : RequestBody() {

    override fun contentType(): MediaType = jsonContentType

    @OptIn(ExperimentalSerializationApi::class)
    override fun writeTo(sink: BufferedSink) {
        Json.encodeToStream(serializer, value, sink.outputStream())
    }

    companion object {

        private val jsonContentType = "application/json; charset=utf-8".toMediaType()
    }
}
