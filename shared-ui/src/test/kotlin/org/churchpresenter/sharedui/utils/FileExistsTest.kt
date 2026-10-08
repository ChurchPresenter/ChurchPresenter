@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileExistsTest {

    @Test
    fun `a check asks the disk and remembers the answer`() {
        val file = Files.createTempFile("cp-exists", ".bin")
        val path = file.absolutePathString()
        assertNull(FileExists.known(path))

        assertTrue(FileExists.check(path))
        assertEquals(true, FileExists.known(path))

        Files.delete(file)
        assertFalse(FileExists.check(path))
        assertEquals(false, FileExists.known(path))
    }

    @Test
    fun `a blank path is missing at once, with no check`() = runComposeUiTest {
        var seen: Boolean? = null
        var path by mutableStateOf<String?>(null)
        setContent { seen = rememberFileExists(path) }
        waitForIdle()
        assertEquals(false, seen)

        path = "  "
        waitForIdle()
        assertEquals(false, seen)
    }

    @Test
    fun `a path is unknown until the background check answers, then is what the disk said`() = runComposeUiTest {
        val file = Files.createTempFile("cp-exists", ".bin")
        try {
            var seen: Boolean? = null
            setContent { seen = rememberFileExists(file.absolutePathString()) }
            waitUntil(timeoutMillis = WAIT_MS) { seen == true }
            assertEquals(true, seen)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a path seen before answers on the first composition, and a changed path is checked afresh`() =
        runComposeUiTest {
            val present = Files.createTempFile("cp-exists", ".bin")
            try {
                assertTrue(FileExists.check(present.absolutePathString()))
                var path by mutableStateOf(present.absolutePathString())
                val firstFrame = mutableListOf<Boolean?>()
                var seen: Boolean? = null
                setContent {
                    seen = rememberFileExists(path)
                    if (firstFrame.isEmpty()) firstFrame += seen
                }
                assertEquals(listOf<Boolean?>(true), firstFrame)

                path = present.absolutePathString() + ".gone"
                waitUntil(timeoutMillis = WAIT_MS) { seen == false }
                assertEquals(false, seen)
            } finally {
                Files.deleteIfExists(present)
            }
        }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
