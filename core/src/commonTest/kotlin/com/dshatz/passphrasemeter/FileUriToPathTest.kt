package com.dshatz.passphrasemeter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileUriToPathTest {

    @Test
    fun fileUris() {
        assertEquals("/a/b/en.dict", fileUriToPath("file:///a/b/en.dict"))
        assertEquals("/a/b/en.dict", fileUriToPath("file:/a/b/en.dict"))
        assertEquals("/a/b/en.dict", fileUriToPath("file://localhost/a/b/en.dict"))
        assertEquals("/a/b/en.dict", fileUriToPath("/a/b/en.dict"))
    }

    @Test
    fun iosBundleUri() {
        // Shape of Res.getUri(...) on iOS: percent-encoded file URL into the app bundle.
        assertEquals(
            "/private/var/containers/Bundle/Application/X/My App.app/compose-resources/composeResources/app.res/files/en.dict",
            fileUriToPath("file:///private/var/containers/Bundle/Application/X/My%20App.app/compose-resources/composeResources/app.res/files/en.dict")
        )
    }

    @Test
    fun percentDecodingIsUtf8() {
        assertEquals("/tmp/slovník.dict", fileUriToPath("file:///tmp/slovn%C3%ADk.dict"))
    }

    @Test
    fun windowsDrive() {
        assertEquals("C:/dicts/en.dict", fileUriToPath("file:///C:/dicts/en.dict"))
    }

    @Test
    fun unsupported() {
        assertNull(fileUriToPath("jar:file:/app.jar!/composeResources/files/en.dict"))
        assertNull(fileUriToPath("content://com.example/en.dict"))
        assertNull(fileUriToPath("file://otherhost/en.dict"))
        assertNull(fileUriToPath("file:///bad%2"))
        assertNull(fileUriToPath("relative/en.dict"))
    }
}
