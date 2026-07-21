package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import org.churchpresenter.app.churchpresenter.data.StockMediaClient
import java.io.File
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Searching Pexels/Pixabay from inside the app, and pulling a result down as a background.
 *
 * Everything here is about not misleading the operator while the network is involved: a failed
 * search has to name its cause (a wrong API key and a rate limit need different fixes), paging must
 * accumulate rather than replace, and a superseded search must never win — without cancelling the
 * previous request, a slow first query resolving after a fast second one would leave results that
 * don't match what is typed in the box.
 *
 * [StockMediaClient] is a singleton that talks to the real APIs, so it is replaced with
 * `mockkObject`; no HTTP request is made. The view model does its work in coroutines on
 * `Dispatchers.Main`, which on desktop is the Swing event queue — so [settle] drains that queue
 * instead of polling for a result. Note that polling `isLoading` would NOT work here: it is set
 * inside the launched coroutine, so a poll can pass before the search has even been dispatched.
 * The two tests that deliberately park a request mid-flight wait on observable state instead.
 */
class StockMediaViewModelTest {

    private lateinit var dir: File
    private val created = mutableListOf<StockMediaViewModel>()

    @BeforeTest
    fun stubClient() {
        dir = Files.createTempDirectory("cp-stock-media-test").toFile()
        mockkObject(StockMediaClient)
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        unmockkObject(StockMediaClient)
        dir.deleteRecursively()
    }

    private fun vm(
        mediaType: StockMediaClient.StockMediaType = StockMediaClient.StockMediaType.PHOTO,
        source: StockMediaClient.StockSource = StockMediaClient.StockSource.PEXELS,
    ) = StockMediaViewModel(mediaType, source).also { created.add(it) }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    /**
     * Runs the view model's pending work to completion.
     *
     * `Dispatchers.Main` on desktop is the Swing event queue, and each of these view-model calls
     * dispatches one task onto it that runs start-to-finish (the stubbed client never actually
     * suspends). Draining the queue is therefore an exact wait rather than a poll — and unlike
     * polling `isLoading`, it cannot pass before the work has even been dispatched.
     */
    private fun settle() = repeat(2) { SwingUtilities.invokeAndWait { } }

    private fun item(id: String) = StockMediaClient.StockMediaItem(
        id = id,
        source = StockMediaClient.StockSource.PEXELS,
        isVideo = false,
        thumbnailUrl = "https://example.test/$id/thumb.jpg",
        downloadUrl = "https://example.test/$id/full.jpg",
    )

    private fun success(vararg ids: String, hasMore: Boolean = false) =
        StockMediaClient.SearchOutcome.Success(ids.map { item(it) }, hasMore)

    /** Every search returns [outcome]. */
    private fun searchReturns(outcome: StockMediaClient.SearchOutcome) {
        coEvery { StockMediaClient.search(any(), any(), any(), any(), any()) } returns outcome
    }

    /** Searches for page [page] return [outcome]. */
    private fun searchPageReturns(page: Int, outcome: StockMediaClient.SearchOutcome) {
        coEvery { StockMediaClient.search(any(), any(), any(), any(), page) } returns outcome
    }

    /** Runs a search that lands, so the view model has results and a known page. */
    private fun StockMediaViewModel.searchFor(text: String, key: String = "api-key"): StockMediaViewModel {
        query = text
        search(key)
        settle()
        return this
    }

    // ── Starting state ──────────────────────────────────────────────────────────

    @Test
    fun `a fresh browser has searched for nothing`() {
        val vm = vm()
        assertEquals("", vm.query)
        assertTrue(vm.items.isEmpty())
        assertFalse(vm.isLoading)
        assertNull(vm.searchError)
        assertNull(vm.downloadError)
        assertNull(vm.downloadingId)
        assertFalse(vm.hasMore, "there is no next page of nothing")
    }

    // ── Searching ───────────────────────────────────────────────────────────────

    @Test
    fun `a search with no api key configured is not attempted`() {
        val vm = vm()
        vm.query = "mountains"

        vm.search("")

        assertFalse(vm.isLoading, "the spinner must not start for a request that will never be made")
        coVerify(exactly = 0) { StockMediaClient.search(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an empty search box is not searched for`() {
        val vm = vm()
        vm.search("api-key")
        coVerify(exactly = 0) { StockMediaClient.search(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a search asks the configured source for the typed query`() {
        searchReturns(success("a"))
        val vm = vm(StockMediaClient.StockMediaType.VIDEO, StockMediaClient.StockSource.PIXABAY)

        vm.searchFor("sunrise")

        coVerify(exactly = 1) {
            StockMediaClient.search(
                StockMediaClient.StockSource.PIXABAY,
                "api-key",
                StockMediaClient.StockMediaType.VIDEO,
                "sunrise",
                1,
            )
        }
    }

    @Test
    fun `results are published and the spinner stops`() {
        searchReturns(success("a", "b", "c"))
        val vm = vm()

        vm.searchFor("mountains")

        assertEquals(listOf("a", "b", "c"), vm.items.map { it.id })
        assertFalse(vm.isLoading)
        assertNull(vm.searchError)
    }

    @Test
    fun `a source that repeats a result across the page shows it once`() {
        searchReturns(success("a", "b", "a"))
        val vm = vm()

        vm.searchFor("mountains")

        assertEquals(listOf("a", "b"), vm.items.map { it.id }, "a duplicate would render as a duplicate tile")
    }

    @Test
    fun `whether there is another page comes from the source`() {
        searchReturns(success("a", hasMore = true))
        val vm = vm().searchFor("mountains")
        assertTrue(vm.hasMore)

        searchReturns(success("a", hasMore = false))
        vm.searchFor("mountains")
        assertFalse(vm.hasMore)
    }

    @Test
    fun `a search with no results is not an error`() {
        searchReturns(success())
        val vm = vm()

        vm.searchFor("nothing matches this")

        assertTrue(vm.items.isEmpty())
        assertNull(vm.searchError, "'no results' and 'the request failed' are different messages")
    }

    @Test
    fun `each kind of failure is reported as itself`() {
        val cases = mapOf(
            StockMediaClient.SearchOutcome.InvalidKey to StockSearchError.INVALID_KEY,
            StockMediaClient.SearchOutcome.RateLimited to StockSearchError.RATE_LIMITED,
            StockMediaClient.SearchOutcome.NetworkError to StockSearchError.NETWORK_ERROR,
            StockMediaClient.SearchOutcome.Failure to StockSearchError.FAILURE,
        )
        cases.forEach { (outcome, expected) ->
            searchReturns(outcome)
            val vm = vm()

            vm.searchFor("mountains")

            assertEquals(expected, vm.searchError, "a wrong key and a rate limit need different fixes")
            assertFalse(vm.isLoading, "the spinner must stop even when the request failed")
        }
    }

    @Test
    fun `a failed search leaves the previous results on screen`() {
        searchReturns(success("a", "b"))
        val vm = vm().searchFor("mountains")

        searchReturns(StockMediaClient.SearchOutcome.NetworkError)
        vm.searchFor("rivers")

        assertEquals(listOf("a", "b"), vm.items.map { it.id }, "blanking the grid on a blip loses what was found")
    }

    @Test
    fun `a successful search clears the previous failure`() {
        searchReturns(StockMediaClient.SearchOutcome.RateLimited)
        val vm = vm().searchFor("mountains")

        searchReturns(success("a"))
        vm.searchFor("mountains")

        assertNull(vm.searchError, "a stale error banner over fresh results is wrong")
    }

    @Test
    fun `a new search starts again from the first page`() {
        searchPageReturns(1, success("a", hasMore = true))
        searchPageReturns(2, success("b", hasMore = true))
        val vm = vm().searchFor("mountains")
        vm.loadMore("api-key")
        settle()

        vm.searchFor("rivers")

        assertEquals(listOf("a"), vm.items.map { it.id }, "a new query must not keep the old query's pages")
        coVerify(exactly = 2) { StockMediaClient.search(any(), any(), any(), any(), 1) }
    }

    // ── Paging ──────────────────────────────────────────────────────────────────

    @Test
    fun `the next page is appended to what is already shown`() {
        searchPageReturns(1, success("a", "b", hasMore = true))
        searchPageReturns(2, success("c", "d", hasMore = false))
        val vm = vm().searchFor("mountains")

        vm.loadMore("api-key")
        settle()

        assertEquals(listOf("a", "b", "c", "d"), vm.items.map { it.id })
        assertFalse(vm.hasMore, "the source said that was the last page")
    }

    @Test
    fun `a result repeated on the next page is not shown twice`() {
        searchPageReturns(1, success("a", "b", hasMore = true))
        searchPageReturns(2, success("b", "c", hasMore = false))
        val vm = vm().searchFor("mountains")

        vm.loadMore("api-key")
        settle()

        assertEquals(listOf("a", "b", "c"), vm.items.map { it.id }, "paged APIs re-send items when the feed shifts")
    }

    @Test
    fun `paging keeps walking forward`() {
        searchPageReturns(1, success("a", hasMore = true))
        searchPageReturns(2, success("b", hasMore = true))
        searchPageReturns(3, success("c", hasMore = false))
        val vm = vm().searchFor("mountains")

        vm.loadMore("api-key")
        settle()
        vm.loadMore("api-key")
        settle()

        assertEquals(listOf("a", "b", "c"), vm.items.map { it.id })
        coVerify(exactly = 1) { StockMediaClient.search(any(), any(), any(), any(), 3) }
    }

    @Test
    fun `there is nothing to load when the source said that was the last page`() {
        searchReturns(success("a", hasMore = false))
        val vm = vm().searchFor("mountains")

        vm.loadMore("api-key")

        coVerify(exactly = 1) { StockMediaClient.search(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `loading more without a key or a query does nothing`() {
        searchReturns(success("a", hasMore = true))
        val vm = vm().searchFor("mountains")

        vm.loadMore("")
        vm.query = ""
        vm.loadMore("api-key")

        coVerify(exactly = 1) { StockMediaClient.search(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed next page stops the list asking for more`() {
        searchPageReturns(1, success("a", "b", hasMore = true))
        searchPageReturns(2, StockMediaClient.SearchOutcome.NetworkError)
        val vm = vm().searchFor("mountains")

        vm.loadMore("api-key")
        settle()

        assertFalse(vm.hasMore, "an endless-scroll that keeps retrying a failing page hammers the API")
        assertEquals(listOf("a", "b"), vm.items.map { it.id }, "what was already found stays")
    }

    // ── Superseded searches ─────────────────────────────────────────────────────

    @Test
    fun `a slow search that is superseded never overwrites the newer one`() {
        // Which request parks is keyed on the QUERY, not on call order: both searches are dispatched
        // to the event queue, so the order they reach the client in is not something a test may
        // assume. "mountains" hangs until released; "rivers" answers at once.
        val slowSearchStarted = CompletableDeferred<Unit>()
        val releaseSlowSearch = CompletableDeferred<Unit>()
        coEvery { StockMediaClient.search(any(), any(), any(), "mountains", any()) } coAnswers {
            slowSearchStarted.complete(Unit)
            releaseSlowSearch.await()
            success("stale")
        }
        coEvery { StockMediaClient.search(any(), any(), any(), "rivers", any()) } returns success("fresh")
        val vm = vm()

        vm.query = "mountains"
        vm.search("api-key")
        // Supersede it only once it is genuinely in flight — otherwise the cancellation would be
        // of a request that had not started, which is not the case being tested.
        awaitUntil("the first search to reach the network") { slowSearchStarted.isCompleted }
        vm.query = "rivers"
        vm.search("api-key")
        awaitUntil("the second search to land") { vm.items.map { it.id } == listOf("fresh") }

        releaseSlowSearch.complete(Unit)   // the superseded request finally comes back
        settle()                           // and gets its chance to run on the event queue

        assertEquals(
            listOf("fresh"),
            vm.items.map { it.id },
            "results for a query the operator has already replaced must never win",
        )
    }

    // ── Downloading ─────────────────────────────────────────────────────────────

    @Test
    fun `downloading hands back the saved file`() {
        val saved = File(dir, "sunrise.jpg").also { it.writeText("x") }
        coEvery { StockMediaClient.download(any()) } returns StockMediaClient.DownloadOutcome.Success(saved)
        val vm = vm()
        var handedBack: String? = null

        vm.download(item("a")) { handedBack = it }

        settle()
        assertEquals(saved.absolutePath, handedBack)
        assertNull(vm.downloadError)
        assertNull(vm.downloadingId, "the tile's spinner must stop when the download ends")
    }

    @Test
    fun `a failed download is reported and hands nothing back`() {
        val cases = mapOf(
            StockMediaClient.DownloadOutcome.NetworkError to StockDownloadError.NETWORK_ERROR,
            StockMediaClient.DownloadOutcome.Failure to StockDownloadError.FAILURE,
        )
        cases.forEach { (outcome, expected) ->
            coEvery { StockMediaClient.download(any()) } returns outcome
            val vm = vm()
            var handedBack: String? = null

            vm.download(item("a")) { handedBack = it }

            settle()
            assertEquals(expected, vm.downloadError)
            assertNull(handedBack, "a failed download must not be treated as a chosen background")
            assertNull(vm.downloadingId)
        }
    }

    @Test
    fun `the tile being downloaded is marked while it runs`() {
        val parked = CompletableDeferred<Unit>()
        val saved = File(dir, "sunrise.jpg").also { it.writeText("x") }
        coEvery { StockMediaClient.download(any()) } coAnswers {
            parked.await()
            StockMediaClient.DownloadOutcome.Success(saved)
        }
        val vm = vm()

        vm.download(item("chosen")) { }

        awaitUntil("the download to start") { vm.downloadingId == "chosen" }
        parked.complete(Unit)
        awaitUntil("the download to finish") { vm.downloadingId == null }
    }

    @Test
    fun `a retry clears the previous download failure`() {
        coEvery { StockMediaClient.download(any()) } returns StockMediaClient.DownloadOutcome.NetworkError
        val vm = vm()
        vm.download(item("a")) { }
        settle()

        val saved = File(dir, "sunrise.jpg").also { it.writeText("x") }
        coEvery { StockMediaClient.download(any()) } returns StockMediaClient.DownloadOutcome.Success(saved)
        var handedBack: String? = null
        vm.download(item("a")) { handedBack = it }

        settle()
        assertNull(vm.downloadError, "a stale error beside a downloaded file is wrong")
    }

    @Test
    fun `searching and downloading do not report each other's errors`() {
        searchReturns(StockMediaClient.SearchOutcome.RateLimited)
        coEvery { StockMediaClient.download(any()) } returns StockMediaClient.DownloadOutcome.Failure
        val vm = vm().searchFor("mountains")

        vm.download(item("a")) { }
        settle()

        assertEquals(StockSearchError.RATE_LIMITED, vm.searchError, "the two failures have separate banners")
        assertEquals(StockDownloadError.FAILURE, vm.downloadError)
    }
}
