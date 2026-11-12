package com.dshatz.passphrasemeter

actual fun loadTestDictionary(name: String): Boolean {
    val bytes = PassphraseTester::class.java.getResourceAsStream("/dictionaries/$name.dict")?.use { it.readBytes() }
        ?: error("Test dictionary '$name' is not on the test classpath (configured in build.gradle.kts?)")
    return PassphraseTester.loadDictionary(bytes)
}
