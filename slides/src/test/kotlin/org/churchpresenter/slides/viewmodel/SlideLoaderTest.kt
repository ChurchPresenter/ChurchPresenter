package org.churchpresenter.slides.viewmodel

import io.sentry.NoOpTransportFactory
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.presentation.PresentationLoadError
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.cache.SlideCacheSupersededException
import org.churchpresenter.presentationengine.cache.SlideDiskCache
import org.churchpresenter.presentationengine.model.DeckFormat
import org.churchpresenter.presentationengine.model.DeckLoadError
import org.churchpresenter.presentationengine.model.Fidelity
import org.churchpresenter.presentationengine.model.Slide
import org.churchpresenter.presentationengine.model.Step
import org.churchpresenter.presentationengine.model.Timeline
import org.churchpresenter.presentationengine.model.pdfDeck
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.slides.data.HiddenItemsStore
import org.churchpresenter.slides.pdfDeck as realPdf
import org.churchpresenter.slides.tempDir
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SlideLoaderTest {

    private val dir = tempDir("cp-slide-loader")
    private val cache = SlideDiskCache(File(dir, "cache"))
    private var seq = 0

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private class Parts(val state: PresentationState, val loader: SlideLoader)

    private fun parts(appSettings: AppSettings? = AppSettings()): Parts {
        val state = PresentationState(appSettings)
        val hidden = HiddenSlides(state, HiddenItemsStore(File(dir, "hidden.json")))
        val loader = SlideLoader(state, appSettings, cache, hidden, SlidePlayback(state))
        return Parts(state, loader)
    }

    private fun slide(index: Int, builds: Int = 0) = Slide(
        index = index,
        notes = "note $index",
        transition = null,
        layers = emptyList(),
        timeline = if (builds > 0) Timeline(List(builds) { Step(emptyList()) }) else null,
        fidelity = Fidelity.NATIVE,
    )

    private fun frame() = BufferedImage(32, 18, BufferedImage.TYPE_INT_RGB)

    private fun file(name: String) = realPdf(dir, 1, name)

    /** Runs [block] on the caller's thread, or already on Main so every hop to Main is immediate. */
    private fun run(onMain: Boolean, block: suspend () -> Unit) = runBlocking {
        if (onMain) withContext(Dispatchers.Main) { block() } else block()
    }

    private fun everyWay(test: (onMain: Boolean) -> Unit) {
        test(false)
        test(true)
        withReporting { test(false) }
    }

    private fun withReporting(block: () -> Unit) {
        Sentry.init { options ->
            options.dsn = "https://key@localhost/1"
            options.setTransportFactory(NoOpTransportFactory.getInstance())
            options.isEnableUncaughtExceptionHandler = false
            options.isEnableAutoSessionTracking = false
        }
        try {
            assertTrue(CrashReporter.isEnabled())
            block()
        } finally {
            Sentry.close()
        }
    }

    @Test
    fun `a rendered deck lands every slide, commits the cache and bumps the generation`() = everyWay { onMain ->
        val p = parts()
        val f = file("ok-${seq++}.pdf")
        p.loader.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0, builds = 1), slide(1)))) }
        p.loader.renderSlideFrame = { _, _ -> frame() }
        run(onMain) { p.loader.loadOrCacheSlides(f) }
        assertEquals(2, p.state.slideFiles.size)
        assertEquals(listOf("note 0", "note 1"), p.state.slideNotes.toList())
        assertEquals(1, p.state.loadGeneration.value)
        assertFalse(p.state.isLoading.value)
        assertNotNull(cache.lookup(f, null))
    }

    @Test
    fun `a second load of the same deck comes from the cache`() = everyWay { onMain ->
        val p = parts()
        val f = file("cached-${seq++}.pdf")
        p.loader.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0), slide(1)))) }
        p.loader.renderSlideFrame = { _, _ -> frame() }
        run(onMain) { p.loader.loadOrCacheSlides(f) }
        p.loader.renderSlideFrame = { _, _ -> error("must not render a cached deck") }
        run(onMain) { p.loader.loadOrCacheSlides(f) }
        assertEquals(2, p.state.slideFiles.size)
        assertEquals(2, p.state.totalSlides.value)
        assertEquals(2, p.state.loadGeneration.value)
        assertNotNull(p.state.deck.value)
    }

    @Test
    fun `a deck that fails to load sets its error and writes no cache`() = everyWay { onMain ->
        val p = parts(appSettings = null)
        val f = file("fail-${seq++}.pdf")
        p.loader.loadDeck = { LoadResult.Failure(DeckLoadError.PARSE_FAILED) }
        run(onMain) { p.loader.loadOrCacheSlides(f) }
        assertEquals(PresentationLoadError.RENDER_FAILED, p.state.loadError.value)
        assertFalse(p.state.isLoading.value)
        assertNull(cache.lookup(f, null))
    }

    @Test
    fun `an operator's locked deck sets its own error`() = everyWay { onMain ->
        val p = parts()
        val f = file("locked-${seq++}.pdf")
        p.loader.loadDeck = { LoadResult.Failure(DeckLoadError.PASSWORD_PROTECTED) }
        run(onMain) { p.loader.loadOrCacheSlides(f) }
        assertEquals(PresentationLoadError.PASSWORD_PROTECTED, p.state.loadError.value)
    }

    @Test
    fun `a loader that throws is a render failure`() = everyWay { onMain ->
        val p = parts()
        val f = file("throws-${seq++}.pdf")
        p.loader.loadDeck = { throw IllegalStateException("parser crashed") }
        run(onMain) { p.loader.loadOrCacheSlides(f) }
        assertEquals(PresentationLoadError.RENDER_FAILED, p.state.loadError.value)
        assertFalse(p.state.isLoading.value)
    }

    @Test
    fun `a deck whose slides all fail is a render failure and leaves no cache`() = everyWay { onMain ->
        val p = parts()
        val f = file("allbad-${seq++}.pdf")
        p.loader.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0), slide(1)))) }
        p.loader.renderSlideFrame = { _, _ -> error("nothing renders") }
        run(onMain) { p.loader.loadOrCacheSlides(f) }
        assertEquals(PresentationLoadError.RENDER_FAILED, p.state.loadError.value)
        assertTrue(p.state.slideFiles.isEmpty())
        assertNull(cache.lookup(f, null))
    }

    @Test
    fun `a render cancelled mid-deck rethrows and aborts its cache entry`() = everyWay { onMain ->
        val p = parts()
        val f = file("cancel-${seq++}.pdf")
        p.loader.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0), slide(1)))) }
        p.loader.renderSlideFrame = { _, index -> if (index == 1) throw CancellationException("superseded") else frame() }
        assertFailsWith<CancellationException> { run(onMain) { p.loader.loadOrCacheSlides(f) } }
        assertEquals(1, p.state.slideFiles.size)
        assertFalse(p.state.isLoading.value)
        assertFalse(cache.isWriting(f))
        assertNull(cache.lookup(f, null))
    }

    @Test
    fun `a newer render taking the entry mid-deck ends this one without an error`() = everyWay { onMain ->
        val p = parts()
        val f = file("taken-${seq++}.pdf")
        p.loader.loadDeck = { LoadResult.Success(pdfDeck(f, listOf(slide(0), slide(1)))) }
        var newer: SlideDiskCache.Writer? = null
        p.loader.renderSlideFrame = { _, index ->
            if (index == 1) newer = cache.beginWrite(f, DeckFormat.PDF, DEFAULT_WIDTH)
            frame()
        }
        run(onMain) { p.loader.loadOrCacheSlides(f) }
        assertNull(p.state.loadError.value)
        assertEquals(0, p.state.loadGeneration.value)
        assertFalse(p.state.isLoading.value)
        assertTrue(cache.isWriting(f), "the newer writer still owns the entry")
        newer?.abort()
    }

    @Test
    fun `slides already downloaded are reused and missing ones are fetched`() = everyWay { onMain ->
        val p = parts()
        val cacheDir = File(dir, "remote-ok-${seq++}").apply { mkdirs() }
        File(cacheDir, "slide_0000.jpg").writeBytes(byteArrayOf(1))
        val fetched = mutableListOf<Int>()
        run(onMain) {
            p.loader.downloadSlides(cacheDir, 2) { index -> fetched += index; byteArrayOf(2) }
        }
        assertEquals(listOf(1), fetched)
        assertEquals(2, p.state.slideFiles.size)
        assertEquals(listOf("", ""), p.state.slideNotes.toList())
        assertEquals(1, p.state.loadGeneration.value)
        assertFalse(p.state.isLoading.value)
        assertTrue(cacheDir.isDirectory)
    }

    @Test
    fun `a download that lands nothing fails and removes its folder`() = everyWay { onMain ->
        val p = parts()
        val cacheDir = File(dir, "remote-none-${seq++}").apply { mkdirs() }
        run(onMain) { p.loader.downloadSlides(cacheDir, 2) { null } }
        assertEquals(PresentationLoadError.RENDER_FAILED, p.state.loadError.value)
        assertFalse(cacheDir.exists())
        assertFalse(SlideDiskCache.isOwned(cacheDir))
    }

    @Test
    fun `a slide that cannot be moved into place is skipped`() = everyWay { onMain ->
        val p = parts()
        val cacheDir = File(dir, "remote-blocked-${seq++}").apply { mkdirs() }
        run(onMain) {
            p.loader.downloadSlides(cacheDir, 1) {
                File(cacheDir, "slide_0000.jpg/inside").apply { parentFile.mkdirs(); writeText("x") }
                byteArrayOf(3)
            }
        }
        assertTrue(p.state.slideFiles.isEmpty())
        assertEquals(PresentationLoadError.RENDER_FAILED, p.state.loadError.value)
    }

    @Test
    fun `a cancelled download leaves its folder to the load that replaced it`() {
        val p = parts()
        val cacheDir = File(dir, "remote-cancel").apply { mkdirs() }
        assertFailsWith<CancellationException> {
            runBlocking {
                p.loader.downloadSlides(cacheDir, 2) { index ->
                    if (index == 1) {
                        CoroutineScope(currentCoroutineContext()).cancel()
                        throw CancellationException("replaced")
                    }
                    byteArrayOf(4)
                }
            }
        }
        assertTrue(cacheDir.isDirectory)
        assertFalse(SlideDiskCache.isOwned(cacheDir))
    }

    @Test
    fun `a pdf deck is always exposed, and no deck stays none`() {
        val p = parts(appSettings = null)
        val deck = pdfDeck(file("exposed.pdf"), listOf(slide(0)))
        assertSame(deck, p.loader.exposableDeck(deck))
        assertNull(p.loader.exposableDeck(null))
    }

    private companion object {
        const val DEFAULT_WIDTH = 1920
    }
}
