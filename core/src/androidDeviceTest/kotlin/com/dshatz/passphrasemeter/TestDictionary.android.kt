package com.dshatz.passphrasemeter

import androidx.test.platform.app.InstrumentationRegistry

// The test APK's own context: dictionaries are packaged as assets of the test APK.
actual fun loadTestDictionary(name: String): Boolean = PassphraseTester.loadDictionaryFromAsset(
    InstrumentationRegistry.getInstrumentation().context,
    "dictionaries/$name.dict"
)
