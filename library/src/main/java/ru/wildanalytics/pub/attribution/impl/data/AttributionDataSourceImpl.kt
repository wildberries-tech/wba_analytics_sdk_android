package ru.wildanalytics.pub.attribution.impl.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import ru.gildor.coroutines.okhttp.await
import ru.wildanalytics.pub.analytics.api.JsonBody
import ru.wildanalytics.pub.analytics.transport.HttpTimeouts
import ru.wildanalytics.pub.attribution.api.WildAttributionLogger
import ru.wildanalytics.pub.attribution.impl.fingerprint.DeviceFingerprintCollector
import ru.wildanalytics.pub.attribution.impl.fingerprint.DeviceFingerprintDto
import java.util.concurrent.TimeUnit
import kotlin.time.toJavaDuration

private const val ATTRIBUTION_URL = "https://wildtracker.wb.ru/fingerprint/check"
private const val PREFERENCES_NAME = "ru.wildanalytics.pub.analytics.attribution"
private const val IS_ATTRIBUTION_CHECKED_KEY = "isAttributionChecked"
private const val ATTRIBUTION_DATA_KEY = "attributionData"

internal class AttributionDataSourceImpl(
    private val fingerprintCollector: DeviceFingerprintCollector,
    private val log: WildAttributionLogger,
    private val preferences: SharedPreferences,
) : AttributionDataSource {

    constructor(context: Context, log: WildAttributionLogger) : this(
        fingerprintCollector = DeviceFingerprintCollector(context),
        log = log,
        preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    )


    @OptIn(InternalSerializationApi::class)
    override suspend fun getAttributionResult(): AttributionResultDto? = onIo {
        val fingerprint = fingerprintCollector.collect()
        val data = getPersistedData()
            ?: getRemoteData(fingerprint)?.also { persistData(it) }
        AttributionResultDto(fingerprintGathered = data, userAttributes = fingerprint)
    }

    private fun persistData(data: JsonObject) {
        preferences.edit {
            putString(ATTRIBUTION_DATA_KEY, Json.encodeToString(data))
        }
    }

    @InternalSerializationApi
    private suspend fun getRemoteData(fingerprint: DeviceFingerprintDto): JsonObject? {
        log.logDebug { "fingerprint is $fingerprint" }
        val client = createOkHttpClient()
        val request = Request.Builder()
            .url(ATTRIBUTION_URL)
            .post(JsonBody(fingerprint, DeviceFingerprintDto.serializer()))
            .build()
        val response = client.newCall(request).await()
        val data = response.use {
            if (response.isSuccessful) {
                decodeData(response)
            } else if (response.code == 404) {
                null
            } else {
                throw IOException("Request failed with ${response.code} code, ${response.message} message")
            }
        }
        return data
    }

    override suspend fun isAttributionChecked(): Boolean = onIo {
        preferences.getBoolean(IS_ATTRIBUTION_CHECKED_KEY, false)
    }

    override suspend fun setAttributionChecked() = onIo {
        preferences.edit {
            putBoolean(IS_ATTRIBUTION_CHECKED_KEY, true)
        }
    }

    @InternalSerializationApi
    override fun decodeAttributionDataJson(json: JsonObject): AttributionDataDto? {
        return try {
            Json.decodeFromJsonElement<AttributionDataDto>(json)
        } catch (e: Exception) {
            log.logError(e) { "Failed to parse attribution data" }
            null
        }
    }

    @OptIn(InternalSerializationApi::class)
    private fun getPersistedData(): JsonObject? =
        preferences.getString(ATTRIBUTION_DATA_KEY, null)
            ?.let {
                try {
                    Json.decodeFromString<JsonObject>(it)
                } catch (e: Exception) {
                    log.logError(e) { "Failed to parse attribution data" }
                    null
                }
            }

    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    private fun decodeData(response: Response): JsonObject? =
        response.body?.byteStream()?.use { stream ->
            Json.decodeFromStream(JsonObject.serializer(), stream)
        }

    private suspend fun <T> onIo(action: suspend () -> T): T =
        withContext(Dispatchers.IO) { action() }

    private fun createOkHttpClient(timeouts: HttpTimeouts = HttpTimeouts()) = OkHttpClient.Builder()
        .connectTimeout(timeouts.connectTimeout.toJavaDuration())
        .readTimeout(timeouts.readTimeout.toJavaDuration())
        .writeTimeout(timeouts.writeTimeout.toJavaDuration())
        // попытка избавиться от отвалившихся сокетов, вызывающих java.net.SocketTimeoutException при реконнекте
        // https://github.com/square/okhttp/issues/3146
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .build()
}
