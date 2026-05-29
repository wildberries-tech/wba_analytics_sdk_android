package ru.wildanalytics.pub.analytics.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.wildanalytics.pub.analytics.CoroutineScopeFactory
import ru.wildanalytics.pub.analytics.WildAnalyticsLogger
import ru.wildanalytics.pub.analytics.logDebug
import ru.wildanalytics.pub.analytics.logWarn
import java.util.concurrent.atomic.AtomicBoolean

private const val AD_ID_SERVICE_INTERFACE_DESCRIPTOR =
    "com.google.android.gms.ads.identifier.internal.IAdvertisingIdService"
private const val GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms"
private const val GMS_AD_ID_BIND_ACTION =
    "com.google.android.gms.ads.identifier.service.START"
private const val MAX_AD_ID_WAIT_TIMEOUT_MS = 100L
private const val ZERO_AD_ID_VALUE = "00000000-0000-0000-0000-000000000000"

/**
 * Клиент для получения рекламного идентификатора Google (GAID).
 * Использует прямой биндинг к сервису Google Play для минимизации зависимостей.
 *
 * Спецификация транзакций соответствует интерфейсу:
 * com.google.android.gms.ads.identifier.internal.IAdvertisingIdService
 */
internal class GoogleAdIdProvider(
    private val context: Context,
    private val log: WildAnalyticsLogger,
    scopeFactory: CoroutineScopeFactory,
) {
    private val state = MutableStateFlow<AdIdState>(AdIdState.Pending)
    private val scope = scopeFactory.create(GoogleAdIdProvider::class.java.simpleName.orEmpty())

    init {
        warmUp()
    }

    /**
     * Возвращает GAID. Если получение невозможно или пользователь ограничил трекинг,
     * возвращает пустую строку или строку из нулей (в зависимости от версии ОС).
     *
     * Дожидается завершения процесса получения ID не более 100мс.
     */
    suspend fun getAdvertisingId(): String =
        withTimeoutOrNull(MAX_AD_ID_WAIT_TIMEOUT_MS) { state.first { it !is AdIdState.Pending }.id }
            .orEmpty()

    private fun warmUp() {
        val intent = Intent(GMS_AD_ID_BIND_ACTION).setPackage(GOOGLE_PLAY_SERVICES_PACKAGE)
        val connection = FetchGAIDServiceConnection()
        try {
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                connection.finalize(AdIdState.NotSupported)
            }
        } catch (e: Exception) {
            connection.finalize(AdIdState.Error(e))
        }
    }

    private inner class FetchGAIDServiceConnection : ServiceConnection {

        private val isFinished = AtomicBoolean(false)

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            scope.launch(Dispatchers.IO) {
                val fetchingState = try {
                    when (val id = service.advertId) {
                        null, ZERO_AD_ID_VALUE, "" -> AdIdState.Limited
                        else -> AdIdState.Available(id)
                    }
                } catch (e: Exception) {
                    AdIdState.Error(e)
                }
                finalize(fetchingState)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit

        override fun onNullBinding(name: ComponentName) {
            finalize(AdIdState.NotSupported)
        }

        override fun onBindingDied(name: ComponentName) {
            finalize(AdIdState.Error(DeadObjectException()))
        }

        fun finalize(finalState: AdIdState) {
            if (isFinished.compareAndSet(false, true)) {
                state.value = finalState
                when (finalState) {
                    is AdIdState.Available -> log.logDebug { "GAID available: ${finalState.id}" }
                    is AdIdState.Error -> log.logException(finalState.cause)
                    AdIdState.Limited -> log.logDebug { "GAID tracking limited by user" }
                    AdIdState.Pending -> log.logWarn { "GAID fetching finalized in $finalState state" }
                    AdIdState.NotSupported -> log.logDebug { "GMS AdId service not supported on this device" }
                }
                try {
                    context.unbindService(this)
                } catch (_: Exception) {
                }
            }
        }
    }
}

private sealed interface AdIdState {

    val id: String get() = ""

    object Pending : AdIdState
    data class Available(override val id: String) : AdIdState
    object Limited : AdIdState
    object NotSupported : AdIdState
    data class Error(val cause: Exception) : AdIdState
}

private val IBinder.advertId: String?
    @Throws(RemoteException::class)
    get() {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(AD_ID_SERVICE_INTERFACE_DESCRIPTOR)
            transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)
            reply.readException()
            reply.readString()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
