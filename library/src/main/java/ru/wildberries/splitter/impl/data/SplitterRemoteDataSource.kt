package ru.wildberries.splitter.impl.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import ru.wildberries.splitter.api.WBSplitterConfig
import ru.wildberries.splitter.impl.SplitterLogger
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

internal class SplitterRemoteDataSource(private val log: SplitterLogger) {
    val client = createOkHttpClient()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getExperiments(
        config: WBSplitterConfig,
    ): List<SplitterGroupDto> {
        log.logDebug { "Requesting experiments for apiKey: ${config.apiKey}" }

        val requestBuilder = Request.Builder()
            .url(config.fetchUrl)
            .get()

        requestBuilder.addHeader("X-API-KEY", config.apiKey)
            .addHeader("X-USER-ID", config.userId)
            .addHeader("SITE-LOCALE", Locale.getDefault().language)
        config.clientId?.let { requestBuilder.addHeader("X-CLIENT-ID", it) }
        config.appVersion
            ?.filter { it.isDigit() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { requestBuilder.addHeader("Wb-AppVersion", it) }

        val request = requestBuilder.build()
        val response = client.newCall(request).await()

        return response.use {
            when (it.code) {
                200 -> {
                    val data = it.body
                        ?.byteStream()
                        ?.let { stream ->
                            json.decodeFromStream<List<SplitterGroupDto>>(stream)
                        }
                    log.logDebug { "Received experiments: $data" }
                    data.orEmpty()
                }

                204 -> {
                    log.logDebug { "No experiments for user." }
                    emptyList()
                }

                else -> {
                    val error = "Request failed with code ${it.code}"
                    log.logError(IOException(error)) { error }
                    throw IOException(error)
                }
            }
        }
    }

    private fun createOkHttpClient() = OkHttpClient.Builder()
        .connectTimeout(40.seconds.toJavaDuration())
        .readTimeout(5.seconds.toJavaDuration())
        .writeTimeout(15.seconds.toJavaDuration())
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .build()
}
