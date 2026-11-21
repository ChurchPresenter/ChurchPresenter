package org.churchpresenter.app.churchpresenter.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.allDrawableResources
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_web
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.MissingResourceException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IconWarmUpTest {

    @Test
    fun `every icon the app ships is read from the jar`() {
        val icons = Res.allDrawableResources.values

        assertTrue(icons.isNotEmpty())
        assertEquals(icons.size, runBlocking { warmIcons() })
    }

    @Test
    fun `an icon that fails to read is counted while the rest go on`() {
        val icons = listOf(Res.drawable.ic_close, Res.drawable.ic_web)
        val read = mutableListOf<DrawableResource>()
        val failures = listOf(MissingResourceException("x"), IOException("x"), IllegalStateException("x"))

        val warmed = runBlocking {
            warmIcons(icons + icons + icons.take(1)) { icon ->
                read += icon
                if (read.size > icons.size) throw failures[read.size - icons.size - 1]
            }
        }

        assertEquals(5, read.size)
        assertEquals(icons.size, warmed)
    }

    @Test
    fun `a cancelled warm-up stops rather than counting the cancellation as a failure`() {
        var reads = 0

        assertFailsWith<CancellationException> {
            runBlocking {
                warmIcons(listOf(Res.drawable.ic_close, Res.drawable.ic_web)) {
                    reads++
                    throw CancellationException("window closed")
                }
            }
        }

        assertEquals(1, reads)
    }
}
