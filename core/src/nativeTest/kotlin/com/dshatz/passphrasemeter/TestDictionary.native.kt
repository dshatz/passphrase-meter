package com.dshatz.passphrasemeter

actual fun loadTestDictionary(name: String): Boolean =
    PassphraseTester.loadDictionaryFromUri("$TEST_DICTIONARIES_DIR$name.dict")
