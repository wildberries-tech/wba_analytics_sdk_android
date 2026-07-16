package ru.wildanalytics.pub.analytics.transport

import okhttp3.Headers
import java.util.concurrent.atomic.AtomicReference

internal interface CustomHeadersRepository {

    fun set(key: String, value: String?)

    fun setAll(src: Headers)

    fun current(): Headers
}

internal class CustomHeadersRepositoryImpl : CustomHeadersRepository {

    private val headers = AtomicReference(Headers.Builder().build())

    override fun set(key: String, value: String?) {
        if (key.isBlank()) return
        headers.updateAndGet { current ->
            val builder = current.newBuilder().removeAll(key)
            if (value != null) builder.add(key, value)
            builder.build()
        }
    }

    override fun setAll(src: Headers) {
        if (src.size == 0) return
        headers.updateAndGet { current ->
            val builder = current.newBuilder()
            for (name in src.names()) {
                builder.removeAll(name)
            }
            for (i in 0 until src.size) {
                builder.add(src.name(i), src.value(i))
            }
            builder.build()
        }
    }

    override fun current(): Headers = headers.get()
}
