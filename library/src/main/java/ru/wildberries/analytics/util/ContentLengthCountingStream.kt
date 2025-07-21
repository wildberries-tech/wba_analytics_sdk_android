package ru.wildberries.analytics.util

import java.io.OutputStream

internal class ContentLengthCountingStream : OutputStream() {

    var length: Long = 0
        private set

    override fun write(b: Int) {
        length++
    }

    override fun write(b: ByteArray) {
        length += b.size
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        length += len
    }
}
