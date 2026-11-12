package com.dshatz.passphrasemeter

/**
 * Loads a test dictionary into [PassphraseTester], e.g. `loadTestDictionary(TestDictionaries.EN)`.
 * Available names are configured in build.gradle.kts; Gradle packages them per platform
 * (classpath resources on JVM, test APK assets on Android, files on native).
 */
expect fun loadTestDictionary(name: String): Boolean
