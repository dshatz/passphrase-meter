package com.dshatz.passphrasemeter

import com.dshatz.kni.annotations.JniCall
import com.dshatz.kni.buffers.ByteBuffer
import com.dshatz.kni.buffers.allocateBuffer
import com.dshatz.kni.buffers.encodeToByteBuffer
import com.dshatz.passphrasemeter.pin.PinTestResult
import com.dshatz.passphrasemeter.pin.PinTester

expect object PassphraseTester: BasePassphraseMeter {
    @JniCall
    override fun testPassword(passwordBytes: ByteBuffer): PasswordStrength

    @JniCall
    override fun loadDictionary(dictionary: ByteBuffer): Boolean

    @JniCall
    override fun loadDictionaryFromFd(fd: Int, offset: Long, length: Long): Boolean

    @JniCall
    override fun loadDictionaryFromFile(path: String): Boolean

    @JniCall
    override fun freeDictionary()

    @JniCall
    override fun hasDictionary(): Boolean
}

abstract class BasePassphraseMeter {

    fun testPin(pin: String): PinTestResult {
        return PinTester.testPin(pin)
    }

    fun testPassword(password: String): PasswordStrength {
        val buf = password.encodeToByteBuffer(nullTerminated = true, wipeOnRelease = true)
        try {
            val result = testPassword(buf)
            return result
        } finally {
            buf.release()
        }
    }

    /**
     * Loads a dictionary from its raw bytes, replacing the current one. The bytes are copied,
     * so the array can be discarded afterwards. Works with any source, e.g. Compose resources:
     * `tester.loadDictionary(Res.readBytes("files/en.dict"))`.
     *
     * @return true if loaded; false if the data is not a valid dictionary (the previously
     * loaded dictionary, if any, is kept).
     */
    fun loadDictionary(dictionary: ByteArray): Boolean {
        if (dictionary.isEmpty()) return false
        val buf = allocateBuffer(dictionary.size.toLong())
        try {
            buf.write(dictionary)
            return loadDictionary(buf)
        } finally {
            buf.release()
        }
    }

    internal abstract fun testPassword(passwordBytes: ByteBuffer): PasswordStrength

    /**
     * Loads a dictionary from a direct buffer holding the whole dictionary file, replacing the
     * current one. The data is copied. Returns false if the data is not a valid dictionary.
     */
    internal abstract fun loadDictionary(dictionary: ByteBuffer): Boolean

    /**
     * Loads a dictionary by reading an open file descriptor, replacing the current one.
     * The descriptor is not closed.
     *
     * @param offset where the dictionary starts (seeks there first); negative = current position.
     * @param length dictionary size in bytes; negative = read until end of file.
     * Matches Android's `AssetFileDescriptor` (`startOffset`, `length`).
     */
    internal abstract fun loadDictionaryFromFd(fd: Int, offset: Long, length: Long): Boolean

    /**
     * Loads a dictionary from a file, replacing the current one. The file is mapped read-only
     * and used in place, so nothing is copied.
     */
    internal abstract fun loadDictionaryFromFile(path: String): Boolean

    /** Frees the loaded dictionary; subsequent tests run without dictionary matching. */
    abstract fun freeDictionary()

    /** Whether a dictionary is currently loaded. */
    abstract fun hasDictionary(): Boolean
}
