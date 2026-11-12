package com.dshatz.passphrasemeter

import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UriDictionariesJvmTest {

    @BeforeTest
    fun setup() = PassphraseTester.initialize()

    @AfterTest
    fun cleanup() = PassphraseTester.freeDictionary()

    private val dictionaryUrl = PassphraseTester::class.java.getResource("/dictionaries/en.dict")!!

    @Test
    fun fileUri() {
        // Test resources are a directory on the classpath, so this is a file: URI (mapped natively).
        val uri = dictionaryUrl.toURI().toString()
        assertTrue(uri.startsWith("file:"), uri)
        assertTrue(PassphraseTester.loadDictionaryFromUri(uri))
        assertEquals(PasswordStrength.VERY_WEAK, PassphraseTester.testPassword("international"))
    }

    @Test
    fun jarUri() {
        // Like Compose resources packaged in a jar.
        val jar = File.createTempFile("dictionaries", ".jar").apply { deleteOnExit() }
        JarOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(JarEntry("composeResources/files/en.dict"))
            dictionaryUrl.openStream().use { it.copyTo(out) }
            out.closeEntry()
        }
        val uri = "jar:${jar.toURI()}!/composeResources/files/en.dict"
        assertTrue(PassphraseTester.loadDictionaryFromUri(uri))
        assertEquals(PasswordStrength.VERY_WEAK, PassphraseTester.testPassword("international"))
    }

    @Test
    fun missingUri() {
        assertEquals(false, PassphraseTester.loadDictionaryFromUri("file:/nonexistent/en.dict"))
        assertEquals(false, PassphraseTester.loadDictionaryFromUri("jar:file:/nonexistent.jar!/en.dict"))
    }
}
