package com.dshatz.passphrasemeter

import com.dshatz.kni.buffers.allocateBuffer
import java.io.InputStream
import java.net.URI
import java.nio.channels.Channels

/** Loads a dictionary from any `java.net.URL` (e.g. `jar:` resources). */
internal fun BasePassphraseMeter.loadDictionaryFromUrl(uri: String): Boolean = try {
    val connection = URI(uri).toURL().openConnection()
    connection.getInputStream().use { loadDictionaryFromStream(it, connection.contentLengthLong) }
} catch (_: Exception) {
    false
}

/**
 * Reads [size] bytes of [stream] straight into a direct buffer, without a heap copy.
 * Falls back to reading into a ByteArray when the size is unknown (<= 0).
 */
internal fun BasePassphraseMeter.loadDictionaryFromStream(stream: InputStream, size: Long): Boolean {
    if (size !in 1..Int.MAX_VALUE) return loadDictionary(stream.readBytes())
    val buffer = allocateBuffer(size)
    try {
        val target = buffer.jvmBuffer.duplicate()
        val channel = Channels.newChannel(stream) // not closed: closing it would close the caller's stream
        while (target.hasRemaining()) {
            if (channel.read(target) < 0) return false // shorter than announced
        }
        return loadDictionary(buffer)
    } finally {
        buffer.release()
    }
}
