package com.dshatz.passphrasemeter

/**
 * Native (iOS, macOS, Linux, Windows): Compose resources are plain files (on iOS inside the app
 * bundle), so zxcvbn maps the file itself - no copy.
 */
internal actual fun loadDictionaryFromPlatformUri(meter: BasePassphraseMeter, uri: String): Boolean {
    val path = fileUriToPath(uri) ?: return false
    return meter.loadDictionaryFromFile(path)
}
