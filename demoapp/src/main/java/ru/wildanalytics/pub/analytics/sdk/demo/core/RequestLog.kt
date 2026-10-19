package ru.wildanalytics.pub.analytics.sdk.demo.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** Журнал запросов SDK: реальные через интерцептор OkHttp и ответы мок-транспорта. */
class RequestLog : Interceptor {

    private val nextId = AtomicLong()
    private val _requests = MutableStateFlow<List<RequestRecord>>(emptyList())
    val requests: StateFlow<List<RequestRecord>> = _requests.asStateFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val response = try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            append(host, code = null, error = e.javaClass.simpleName, isMock = false)
            throw e
        }
        append(host, code = response.code, error = null, isMock = false)
        return response
    }

    fun recordMock(url: String, code: Int?, error: String?) {
        append(url.toHttpUrlOrNull()?.host ?: url, code, error, isMock = true)
    }

    fun clear() {
        _requests.value = emptyList()
    }

    private fun append(host: String, code: Int?, error: String?, isMock: Boolean) {
        val record = RequestRecord(
            id = nextId.incrementAndGet(),
            timestampMillis = System.currentTimeMillis(),
            host = host,
            code = code,
            error = error,
            isMock = isMock,
        )
        _requests.update { (listOf(record) + it).take(MAX_RECORDS) }
    }
}

data class RequestRecord(
    val id: Long,
    val timestampMillis: Long,
    val host: String,
    val code: Int?,
    val error: String?,
    val isMock: Boolean = false,
) {
    val isSuccessful: Boolean
        get() = code in SUCCESS_CODES

    val result: String
        get() = code?.toString() ?: error.orEmpty().removeSuffix(EXCEPTION_SUFFIX)
}

private const val MAX_RECORDS = 300
private const val EXCEPTION_SUFFIX = "Exception"
private val SUCCESS_CODES = 200..299
