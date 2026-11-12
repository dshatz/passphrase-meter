package com.dshatz.passphrasemeter

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import java.io.FileNotFoundException

private const val ANDROID_ASSET_PREFIX = "/android_asset/"

/**
 * Loads a dictionary from a [Uri], replacing the current one:
 * - `file:///android_asset/...` (assets, including Compose resources: `Res.getUri("files/en.dict")`)
 * - `content://...` and `file://...`
 *
 * The native side reads the file descriptor directly, so the dictionary is not copied through
 * the Java heap (except for compressed assets, see [loadDictionaryFromAsset]).
 */
fun BasePassphraseMeter.loadDictionary(context: Context, uri: Uri): Boolean {
    val path = uri.path
    if (uri.scheme == ContentResolver.SCHEME_FILE && path != null && path.startsWith(ANDROID_ASSET_PREFIX)) {
        return loadDictionaryFromAsset(context, path.removePrefix(ANDROID_ASSET_PREFIX))
    }
    val afd = context.contentResolver.openAssetFileDescriptor(uri, "r") ?: return false
    return afd.use { loadDictionary(it) }
}

/**
 * Loads a dictionary from the app's assets, replacing the current one. [assetPath] is relative
 * to the assets root, e.g. `"en.dict"` or, for Compose resources,
 * `"composeResources/<package>.generated.resources/files/en.dict"`.
 *
 * Uncompressed assets are read by the native side through a file descriptor. Compressed assets
 * cannot be opened as a descriptor and are read into memory first; to avoid that, keep
 * dictionaries uncompressed in the app module: `androidResources { noCompress += "dict" }`.
 */
fun BasePassphraseMeter.loadDictionaryFromAsset(context: Context, assetPath: String): Boolean {
    val afd = try {
        context.assets.openFd(assetPath)
    } catch (_: FileNotFoundException) {
        null // compressed asset (or missing - then open() below throws)
    }
    if (afd != null) {
        return afd.use { loadDictionary(it) }
    }
    // available() is the full uncompressed length for assets.
    return context.assets.open(assetPath).use { loadDictionaryFromStream(it, it.available().toLong()) }
}

/**
 * Loads a dictionary from an [AssetFileDescriptor], replacing the current one.
 * The descriptor is not closed.
 */
fun BasePassphraseMeter.loadDictionary(afd: AssetFileDescriptor): Boolean {
    // length is AssetFileDescriptor.UNKNOWN_LENGTH (-1) when the data extends to the end of the file,
    // which the native side treats as "read until EOF".
    // Only seek when the data does not start at 0: content:// descriptors may be pipes, which
    // cannot seek (a freshly opened descriptor is already at 0). Negative offset = don't seek.
    val offset = if (afd.startOffset == 0L) -1L else afd.startOffset
    return loadDictionaryFromFd(afd.parcelFileDescriptor.fd, offset, afd.length)
}
