package com.dshatz.passphrasemeter

/**
 * Loads a dictionary from a resource URI, replacing the current one.
 *
 * Made for Compose Multiplatform resources without depending on Compose: pass the URI from
 * `Res.getUri(...)`, e.g.
 * ```
 * PassphraseTester.loadDictionaryFromUri(Res.getUri("files/en.dict"))
 * ```
 * Supported URIs per platform:
 * - Android: `file:///android_asset/...` (Compose resources), `content://...`, `file://...`.
 *   Uncompressed assets are read natively through a file descriptor; add
 *   `androidResources { noCompress += "dict" }` to the app module to get that.
 * - JVM: anything `java.net.URL` can open, e.g. `jar:file:...!/...` (Compose resources) or `file:...`.
 * - iOS / other native: `file://...` (Compose resources live in the app bundle); read directly by zxcvbn.
 *
 * Alternatively, `loadDictionary(Res.readBytes("files/en.dict"))` works everywhere but goes
 * through a ByteArray copy.
 *
 * @return true if loaded; false if the URI is unsupported/unreadable or not a valid dictionary.
 */
fun BasePassphraseMeter.loadDictionaryFromUri(uri: String): Boolean = loadDictionaryFromPlatformUri(this, uri)

internal expect fun loadDictionaryFromPlatformUri(meter: BasePassphraseMeter, uri: String): Boolean

/**
 * Converts a `file:` URI (or a plain absolute path) to a filesystem path, or returns null for
 * other schemes. Handles `file:/p`, `file:///p`, `file://localhost/p`, percent-encoding (UTF-8)
 * and Windows drive letters (`file:///C:/p` -> `C:/p`).
 */
internal fun fileUriToPath(uri: String): String? {
    if (uri.startsWith("/")) return uri
    if (!uri.startsWith("file:", ignoreCase = true)) return null
    var rest = uri.substring("file:".length)
    if (rest.startsWith("//")) {
        rest = rest.substring(2)
        val slash = rest.indexOf('/')
        if (slash < 0) return null
        val host = rest.substring(0, slash)
        if (host.isNotEmpty() && !host.equals("localhost", ignoreCase = true)) return null
        rest = rest.substring(slash)
    }
    rest = rest.substringBefore('?').substringBefore('#')
    val path = percentDecode(rest) ?: return null
    // "/C:/dir/file" -> "C:/dir/file"
    return if (path.length >= 3 && path[0] == '/' && path[2] == ':' && path[1].isLetter()) path.substring(1) else path
}

private fun percentDecode(s: String): String? {
    if ('%' !in s) return s
    val out = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%') {
            if (i + 2 >= s.length) return null
            val b = s.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
            out.add(b.toByte())
            i += 3
        } else {
            c.toString().encodeToByteArray().forEach { out.add(it) }
            i++
        }
    }
    return out.toByteArray().decodeToString()
}
