package ru.wildanalytics.pub.analytics.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import ru.wildanalytics.pub.analytics.CoroutineScopeFactory
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.logDebug
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private const val MAX_AD_ID_WAIT_TIMEOUT_MS = 100L
private const val BINDER_TIMEOUT_MS = 20000L
private const val ZERO_AD_ID_VALUE = "00000000-0000-0000-0000-000000000000"

internal data class AdIdInfo(val id: String, val type: String)

internal data class IpcServiceConfig(
    val action: String,
    val packageName: String,
    val descriptor: String,
    val providerType: String,
)

/**
 * Клиент для получения рекламного идентификатора (GAID/OAID).
 * Последовательно опрашивает переданные конфигурации сервисов.
 */
internal class AdIdProvider(
    private val context: Context,
    private val log: WildAnalyticsLogger,
    scopeFactory: CoroutineScopeFactory,
    private val configs: List<IpcServiceConfig>,
) {

    private val state = MutableStateFlow<AdIdState>(AdIdState.Pending)
    private val scope = scopeFactory.create(AdIdProvider::class.java.simpleName.orEmpty())

    init {
        scope.launch(Dispatchers.IO) {
            warmUp()
        }
    }

    /**
     * Возвращает рекламный идентификатор и его тип. Если получение невозможно или пользователь ограничил трекинг,
     * возвращает пустые строки.
     *
     * Дожидается завершения процесса получения ID не более 100мс.
     */
    suspend fun getAdIdInfo(): AdIdInfo =
        withTimeoutOrNull(MAX_AD_ID_WAIT_TIMEOUT_MS) { state.first { it !is AdIdState.Pending }.info }
            ?: AdIdInfo("", "")

    private suspend fun warmUp() {
        for (config in configs) {
            val result = performFetch(config)
            if (result is AdIdState.Available) {
                state.value = result
                log.logDebug { "${config.providerType} available: ${result.info.id}" }
                return
            }
        }
        state.value = AdIdState.Unavailable
        log.logDebug { "No Advertising ID available from any provider" }
    }

    private suspend fun performFetch(config: IpcServiceConfig): AdIdState =
        withTimeoutOrNull(BINDER_TIMEOUT_MS) {
            suspendCancellableCoroutine<AdIdState> { continuation ->
                val intent = Intent(config.action).setPackage(config.packageName)
                val connection = FetchAdIdServiceConnection(config, continuation)

                continuation.invokeOnCancellation {
                    connection.finalize(null)
                }

                try {
                    if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                        log.logDebug { "Failed to bind to ${config.providerType} service" }
                        connection.finalize(AdIdState.Unavailable)
                    }
                } catch (e: Exception) {
                    log.logDebug { "Exception binding to ${config.providerType}: ${e.message}" }
                    log.logException(e)
                    connection.finalize(AdIdState.Unavailable)
                }
            }
        } ?: AdIdState.Unavailable.also { log.logDebug { "Timeout fetching from ${config.providerType}" } }

    private inner class FetchAdIdServiceConnection(
        private val config: IpcServiceConfig,
        private val continuation: CancellableContinuation<AdIdState>,
    ) : ServiceConnection {

        private val isFinished = AtomicBoolean(false)

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            scope.launch(Dispatchers.IO) {
                val result = try {
                    val id = service.getAdvertId(config.descriptor)
                    when {
                        id.isNullOrEmpty() -> {
                            log.logDebug { "${config.providerType} returned empty ID" }
                            AdIdState.Unavailable
                        }

                        id == ZERO_AD_ID_VALUE -> {
                            log.logDebug { "${config.providerType} tracking limited by user ($id)" }
                            AdIdState.Unavailable
                        }

                        else -> AdIdState.Available(AdIdInfo(id, config.providerType))
                    }
                } catch (e: Exception) {
                    log.logDebug { "Error fetching from ${config.providerType}: ${e.message}" }
                    log.logException(e)
                    AdIdState.Unavailable
                }
                finalize(result)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit

        override fun onNullBinding(name: ComponentName) {
            log.logDebug { "${config.providerType} returned null binding" }
            finalize(AdIdState.Unavailable)
        }

        override fun onBindingDied(name: ComponentName) {
            log.logDebug { "${config.providerType} binding died" }
            finalize(AdIdState.Unavailable)
        }

        fun finalize(newState: AdIdState?) {
            if (isFinished.compareAndSet(false, true)) {
                try {
                    if (newState != null && continuation.isActive) {
                        continuation.resume(newState)
                    }
                    context.unbindService(this)
                } catch (_: Exception) {
                }
            }
        }
    }
}

private sealed interface AdIdState {
    val info: AdIdInfo get() = AdIdInfo("", "")

    object Pending : AdIdState
    data class Available(override val info: AdIdInfo) : AdIdState
    object Unavailable : AdIdState
}

private fun IBinder.getAdvertId(descriptor: String): String? {
    val data = Parcel.obtain()
    val reply = Parcel.obtain()
    return try {
        data.writeInterfaceToken(descriptor)
        transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)
        reply.readException()
        reply.readString()
    } finally {
        reply.recycle()
        data.recycle()
    }
}
