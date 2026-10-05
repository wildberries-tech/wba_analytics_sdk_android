package ru.wildanalytics.pub.analytics.device

import android.content.Context
import androidx.core.content.edit
import ru.wildanalytics.pub.analytics.util.IdGenerator
import java.util.UUID

internal class WildDeviceInfoProviderImpl(
    context: Context,
    private val idGenerator: IdGenerator,
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
        return idGenerator.generateId()
    }

    private fun transformLongToShortId(id: UUID) = id.leastSignificantBits.toULong().toString()
}
