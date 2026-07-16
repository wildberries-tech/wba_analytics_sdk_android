package ru.wildanalytics.pub.analytics.util

import java.security.SecureRandom
import java.util.UUID

internal interface IdGenerator {
    fun generateId(): UUID
}

internal class RandomIdGenerator : IdGenerator {

    override fun generateId(): UUID {
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
}
