package com.dshatz.passphrasemeter

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

internal actual fun loadDictionaryFromPlatformUri(meter: BasePassphraseMeter, uri: String): Boolean {
    val parsed = Uri.parse(uri)
    return when (parsed.scheme?.lowercase()) {
        "file", "content", "android.resource" -> {
            val context = PassphraseMeterContextProvider.appContext
                ?: error("No Android Context: PassphraseMeterContextProvider was not started (removed from the manifest?)")
            meter.loadDictionary(context, parsed)
        }
        // e.g. jar:file:...!/... from Compose resources in host (unit) tests
        else -> meter.loadDictionaryFromUrl(uri)
    }
}

/**
 * Captures the application [Context] at startup, so [loadDictionaryFromUri] can open
 * `file:///android_asset/...` and `content://` URIs from common code without asking for a Context
 * (the same approach Compose resources use). Registered in the library manifest.
 */
internal class PassphraseMeterContextProvider : ContentProvider() {
    companion object {
        @Volatile
        var appContext: Context? = null
            private set
    }

    override fun onCreate(): Boolean {
        appContext = requireNotNull(context).applicationContext
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
