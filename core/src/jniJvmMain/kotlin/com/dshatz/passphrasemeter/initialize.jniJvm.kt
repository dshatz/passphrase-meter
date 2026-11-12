package com.dshatz.passphrasemeter

import com.dshatz.kni.load.BundledLibLoader

actual fun PassphraseTester.initialize() {
    BundledLibLoader.loadBundledLibrary("core")
}