package org.churchpresenter.lottiegen.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.lottiegen.model.LottieGenConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * What [StyleThumbnails] records about its builds, and the text [ThumbnailDiagnostics.describe]
 * makes of it -- the evidence printed when a Style-menu picture that should be there is not.
 *
 * The render step is a stand-in, as in [StyleThumbnailsTest], scripted per style to come back drawn,
 * null, or throwing. The clock is pinned so the finish time reads the same every run.
 */
class ThumbnailDiagnosticsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val diagnostics = ThumbnailDiagnostics(clock = { FIXED_MS })

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun thumbnails(
        ids: List<String> = listOf("1", "2", "3"),
        render: suspend (String, LottieGenConfig) -> ImageBitmap?,
    ) = StyleThumbnails(scope, diagnostics, render = render, styleIds = { ids }, debounceMs = 0)

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what:\n${diagnostics.describe()}" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `before anything is asked for it says so`() {
        val text = diagnostics.describe()

        assertContains(text, "key present: false, requests: 0")
        assertContains(text, "style asked for: none")
        assertContains(text, "job: never launched")
        assertContains(text, "last build finished: never")
        assertContains(text, "pictures published: 0 []")
    }

    @Test
    fun `a finished build names every style that came back without a picture and why`() {
        val attempts = ConcurrentHashMap<String, Int>()
        val thumbs = thumbnails { _, config ->
            val attempt = attempts.merge(config.style, 1, Int::plus)!!
            when (config.style) {
                "1" -> check(attempt > 1) { "cold load" }
                "2" -> return@thumbnails null
                "3" -> error("never loads")
            }
            ImageBitmap(1, 1)
        }
        thumbs.request(LottieGenConfig(nameText = "Anna", style = "2"))
        waitFor("the build to finish") { "job: completed" in diagnostics.describe() }
        thumbs.request(LottieGenConfig(nameText = "Anna", style = "3"))

        val text = thumbs.diagnostics()
        assertContains(text, "key present: true, requests: 2 (1 for the key already held)")
        assertContains(text, "style asked for: 3")
        assertContains(text, "builds started: 1, superseded: 0")
        assertContains(text, "last build finished: 1970-01-01T00:00:01Z (0 ms ago)")
        assertContains(text, "pictures published: 1 [1]")
        assertContains(text, "asked-for style 3: IllegalStateException twice: never loads")
        assertContains(text, "drawn this build: 1 of 3")
        assertContains(text, "style 1: drawn on the second try (IllegalStateException first)")
        assertContains(text, "style 2: render returned null")
    }

    @Test
    fun `an argument the renderer rejects is named with its message`() {
        val thumbs = thumbnails(ids = listOf("1", "2")) { _, config ->
            require(config.style != "2") { "bad canvas" }
            ImageBitmap(1, 1)
        }
        thumbs.request(LottieGenConfig(nameText = "Anna", style = "1"))
        waitFor("the build to finish") { "job: completed" in diagnostics.describe() }

        val text = diagnostics.describe()
        assertContains(text, "asked-for style 1: drawn")
        assertContains(text, "drawn this build: 1 of 2")
        assertContains(text, "style 2: IllegalArgumentException: bad canvas")
    }

    @Test
    fun `a build overtaken after its picture arrived is counted as superseded`() {
        val drawing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val thumbs = thumbnails(ids = listOf("1")) { _, config ->
            if (config.nameText == "old") {
                drawing.countDown()
                // Blocking, not suspending: the picture comes back after the newer request, so the
                // build sees the key move rather than being cancelled first.
                release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            ImageBitmap(1, 1)
        }
        thumbs.request(LottieGenConfig(nameText = "old"))
        assertTrue(drawing.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "the old build started drawing")
        thumbs.request(LottieGenConfig(nameText = "newer"))
        release.countDown()

        waitFor("the old build to give way") { "superseded: 1" in diagnostics.describe() }
        waitFor("the newer build to finish") { "job: completed" in diagnostics.describe() }
        assertContains(diagnostics.describe(), "builds started: 2, superseded: 1")
    }

    @Test
    fun `a build still drawing reads active, and cancelled once its scope goes`() {
        val never = CompletableDeferred<ImageBitmap?>()
        val thumbs = thumbnails { _, _ -> never.await() }
        thumbs.request(LottieGenConfig(nameText = "Anna", style = "1"))
        waitFor("the build to start") { "builds started: 1" in diagnostics.describe() }
        assertContains(diagnostics.describe(), "job: active")
        assertContains(diagnostics.describe(), "asked-for style 1: no outcome this build")

        scope.cancel()
        waitFor("the build to be cancelled") { "job: cancelled" in diagnostics.describe() }
        assertContains(diagnostics.describe(), "last build finished: never")
    }

    private companion object {
        const val FIXED_MS = 1_000L
        const val TIMEOUT_MS = 5_000L
    }
}
