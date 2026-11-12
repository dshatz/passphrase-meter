package com.dshatz.passphrasemeter

/**
 * JVM: `file:` URIs (e.g. Compose resources in a dev run) are mapped by native code, no copy.
 * Anything else, e.g. `jar:file:...!/...` (Compose resources in a jar), is streamed into a direct buffer.
 */
internal actual fun loadDictionaryFromPlatformUri(meter: BasePassphraseMeter, uri: String): Boolean {
    fileUriToPath(uri)?.let { return meter.loadDictionaryFromFile(it) }
    return meter.loadDictionaryFromUrl(uri)
}
