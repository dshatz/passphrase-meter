package com.dshatz.passphrasemeter

import com.dshatz.kni.annotations.JniCall
import com.dshatz.kni.buffers.ByteBuffer
import external.zxcvbn.ZxcvbnInit
import external.zxcvbn.ZxcvbnInitFromBuffer
import external.zxcvbn.ZxcvbnInitFromFd
import external.zxcvbn.ZxcvbnIsInit
import external.zxcvbn.ZxcvbnMatch
import external.zxcvbn.ZxcvbnUnInit
import external.zxcvbn.zxcvbn_read_lock
import external.zxcvbn.zxcvbn_read_unlock
import external.zxcvbn.zxcvbn_write_lock
import external.zxcvbn.zxcvbn_write_unlock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.get

@OptIn(ExperimentalForeignApi::class)
actual object PassphraseTester : BasePassphraseMeter() {
    @JniCall
    actual override fun testPassword(passwordBytes: ByteBuffer): PasswordStrength = try {
        zxcvbn_read_lock()
        passwordBytes.requireNullTerminated()
        val result = ZxcvbnMatch(passwordBytes.address, null, null)
        val log = result * 0.301029996
        when {
            log < 3 -> PasswordStrength.VERY_WEAK
            log < 6 -> PasswordStrength.WEAK
            log < 8 -> PasswordStrength.MODERATE
            log < 10 -> PasswordStrength.GOOD
            else -> PasswordStrength.STRONG
        }
    } finally {
        zxcvbn_read_unlock()
    }

    @JniCall
    actual override fun loadDictionary(dictionary: ByteBuffer): Boolean = try {
        zxcvbn_write_lock()
        // zxcvbn copies the data, so the (borrowed) buffer may be released right after.
        ZxcvbnInitFromBuffer(dictionary.address, dictionary.capacity.convert()) == 1
    } finally {
        zxcvbn_write_unlock()
    }

    @JniCall
    actual override fun loadDictionaryFromFd(fd: Int, offset: Long, length: Long): Boolean = try {
        zxcvbn_write_lock()
        ZxcvbnInitFromFd(fd, offset, length) == 1
    } finally {
        zxcvbn_write_unlock()
    }

    @JniCall
    actual override fun loadDictionaryFromFile(path: String): Boolean = try {
        zxcvbn_write_lock()
        ZxcvbnInit(path) == 1
    } finally {
        zxcvbn_write_unlock()
    }

    @JniCall
    actual override fun freeDictionary() = try {
        zxcvbn_write_lock()
        ZxcvbnUnInit()
    } finally {
        zxcvbn_write_unlock()
    }

    @JniCall
    actual override fun hasDictionary(): Boolean = try {
        zxcvbn_read_lock()
        ZxcvbnIsInit() == 1
    } finally {
        zxcvbn_read_unlock()
    }

    private fun ByteBuffer.requireNullTerminated() {
        val cap = capacity
        require(cap > 0 && address[cap - 1] == 0.toByte()) { "buffer must be NUL-terminated" }
    }
}
