package ru.wildanalytics.pub.analytics.device

import android.content.Context
import androidx.core.content.edit
import java.security.SecureRandom
import java.util.UUID

internal class WildDeviceInfoProviderImpl(
    context: Context
) : WildDeviceInfoProvider {

    private val preferences = context.getSharedPreferences("ru.wildanalytics.pub.analytics.prefs", Context.MODE_PRIVATE)

    private val deviceId: DeviceId
    private val isUserNew: Boolean

    init {
        var id = getIdFromStorage()
        if (id != null) {
            deviceId = DeviceId(id)
            isUserNew = false
        } else {
            val randomId = createRandomId()
            id = randomId.toString()
            putIdToStorage(id)
            deviceId = DeviceId(transformLongToShortId(randomId))
            isUserNew = true
        }
    }

    override fun getDeviceId() = deviceId

    override fun isUserNew() = isUserNew

    private fun putIdToStorage(id: String) {
        preferences.edit {
            putString("randomDeviceId", id)
        }
    }

    private fun getIdFromStorage(): String? {
        return preferences
            .getString("randomDeviceId", null)
            ?.takeIf { it.isNotEmpty() }
            ?.let { UUID.fromString(it) }
            ?.leastSignificantBits
            ?.toULong()
            ?.toString()
    }

    private fun createRandomId(): UUID {
        return randomUUID()
    }

    /** код из [UUID.randomUUID], но без присваивания [UUID.variant], чтобы уменьшить количество колизий в [UUID.getLeastSignificantBits]*/
    private fun randomUUID(): UUID {
        val random = SecureRandom()
        val randomBytes = ByteArray(16)
        random.nextBytes(randomBytes)
        randomBytes[6] = (randomBytes[6].toInt() and 0x0f).toByte() /* clear version        */
        randomBytes[6] = (randomBytes[6].toInt() or 0x40).toByte() /* set to version 4     */
        return createUUIDFromBytes(randomBytes)
    }

    // код из приватного конструктора UUID(byte[] data)
    private fun createUUIDFromBytes(data: ByteArray): UUID {
        var msb: Long = 0
        var lsb: Long = 0
        assert(data.size == 16) { "data must be 16 bytes in length" }
        for (i in 0..7) msb = (msb shl 8) or (data[i].toLong() and 0xff)
        for (i in 8..15) lsb = (lsb shl 8) or (data[i].toLong() and 0xff)
        return UUID(msb, lsb)
    }

    private fun transformLongToShortId(id: UUID) = id.leastSignificantBits.toULong().toString()
}
