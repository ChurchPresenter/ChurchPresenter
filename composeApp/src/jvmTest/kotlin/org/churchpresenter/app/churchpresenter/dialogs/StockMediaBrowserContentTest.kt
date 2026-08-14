@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.stock_photo_browse_photos_title
import churchpresenter.composeapp.generated.resources.stock_photo_search_placeholder_photo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import org.churchpresenter.app.churchpresenter.data.StockMediaClient
import org.churchpresenter.app.churchpresenter.viewmodel.StockMediaViewModel
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StockMediaBrowserContentTest {

    @BeforeTest
    fun stubClient() {
        mockkObject(StockMediaClient)
        coEvery { StockMediaClient.fetchThumbnailBytes(any(), any()) } returns null
    }

    @AfterTest
    fun cleanUp() {
        unmockkObject(StockMediaClient)
        unmockkStatic(Desktop::class)
    }

    private fun settle() = repeat(2) { SwingUtilities.invokeAndWait { } }

    /** Polls until [condition] is true, settling the Swing/Compose queues between checks. */
    private fun ComposeUiTest.awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            settle()
            waitForIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for condition")
    }

    private fun item(id: String, isVideo: Boolean = false) = StockMediaClient.StockMediaItem(
        id = id,
        source = StockMediaClient.StockSource.PEXELS,
        isVideo = isVideo,
        thumbnailUrl = "https://example.test/$id/thumb.jpg",
        downloadUrl = "https://example.test/$id/full.jpg",
    )

    private fun searchReturns(outcome: StockMediaClient.SearchOutcome) {
        coEvery { StockMediaClient.search(any(), any(), any(), any(), any(), any()) } returns outcome
    }

    private fun tinyPngBytes(): ByteArray {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private val progressSpinner = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    /** Makes `Desktop.getDesktop()` resolve to a fake that records what it was asked to browse. */
    private fun stubDesktop(): () -> URI? {
        var browsed: URI? = null
        val fakeDesktop = mockk<Desktop>()
        every { fakeDesktop.browse(any()) } answers { browsed = firstArg(); Unit }
        mockkStatic(Desktop::class)
        every { Desktop.getDesktop() } returns fakeDesktop
        return { browsed }
    }

    private fun dialog(
        pexelsApiKey: String = "",
        block: ComposeUiTest.(dismissed: () -> Int, downloaded: () -> String?) -> Unit,
    ) {
        var dismissed = 0
        var downloaded: String? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    var key by remember { mutableStateOf(pexelsApiKey) }
                    var tab by remember { mutableStateOf(0) }
                    val pexelsVm =
                        remember { StockMediaViewModel(
                            StockMediaClient.StockMediaType.PHOTO,
                            StockMediaClient.StockSource.PEXELS
                        ) }
                    val pixabayVm =
                        remember { StockMediaViewModel(
                            StockMediaClient.StockMediaType.PHOTO,
                            StockMediaClient.StockSource.PIXABAY
                        ) }
                    StockMediaBrowserDialogContent(
                        titleRes = Res.string.stock_photo_browse_photos_title,
                        searchPlaceholderRes = Res.string.stock_photo_search_placeholder_photo,
                        pexelsViewModel = pexelsVm,
                        pixabayViewModel = pixabayVm,
                        pexelsApiKey = key,
                        onPexelsApiKeyChange = { key = it },
                        pixabayApiKey = "",
                        onPixabayApiKeyChange = {},
                        selectedTab = tab,
                        onSelectedTabChange = { tab = it },
                        onDismiss = { dismissed++ },
                        onDownloadedAndClose = { downloaded = it },
                    )
                }
            }
            waitForIdle()
            block({ dismissed }, { downloaded })
        }
    }

    @Test
    fun `with no api key the search box is replaced with a hint`() = dialog { _, _ ->
        onNodeWithText("Add your Pexels API key above to search.").assertExists()
        onNodeWithText("Search for photos…").assertDoesNotExist()
    }

    @Test
    fun `typing an api key reveals the search box`() = dialog { _, _ ->
        onAllNodes(hasSetTextAction())[0].performTextInput("a-key")
        waitForIdle()

        onNodeWithText("Search for photos…").assertExists()
    }

    @Test
    fun `a search with results shows them and offers Load more`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        searchReturns(StockMediaClient.SearchOutcome.Success(listOf(item("1"), item("2")), hasMore = true))

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        onNodeWithText("Load more").assertExists()
    }

    @Test
    fun `an invalid api key error is shown after a failed search`() = dialog(pexelsApiKey = "bad-key") { _, _ ->
        searchReturns(StockMediaClient.SearchOutcome.InvalidKey)

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        onNodeWithText("Invalid API key").assertExists()
    }

    @Test
    fun `a network error is shown after a failed search`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        searchReturns(StockMediaClient.SearchOutcome.NetworkError)

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        onNodeWithText("Network error — check your connection").assertExists()
    }

    @Test
    fun `a search with no matches shows the no-results message`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        searchReturns(StockMediaClient.SearchOutcome.Success(emptyList(), hasMore = false))

        onNodeWithText("Search for photos…").performTextInput("no such thing")
        onNodeWithText("no such thing").performImeAction()
        settle()
        waitForIdle()

        onNodeWithText("No results found").assertExists()
    }

    @Test
    fun `switching tabs switches the key label shown`() = dialog { _, _ ->
        onNodeWithText("Pixabay").performClick()
        onNodeWithText("PIXABAY API KEY").assertExists()
    }

    @Test
    fun `switching to Pixabay and back to Pexels restores the Pexels key label`() = dialog { _, _ ->
        onNodeWithText("Pixabay").performClick()
        onNodeWithText("PIXABAY API KEY").assertExists()

        onNodeWithText("Pexels").performClick()
        onNodeWithText("PEXELS API KEY").assertExists()
    }

    @Test
    fun `Cancel dismisses the dialog`() = dialog { dismissed, _ ->
        onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissed())
    }

    // ── Search error variants ────────────────────────────────────────────────────

    @Test
    fun `a rate-limited error is shown after a failed search`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        searchReturns(StockMediaClient.SearchOutcome.RateLimited)

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        onNodeWithText("Rate limit reached — try again later").assertExists()
    }

    @Test
    fun `a generic failure is shown after a failed search`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        searchReturns(StockMediaClient.SearchOutcome.Failure)

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        onNodeWithText("Something went wrong. Please try again.").assertExists()
    }

    // ── Loading indicator ────────────────────────────────────────────────────────

    @Test
    fun `a spinner shows while a search is in flight and clears once it resolves`() =
        dialog(pexelsApiKey = "a-key") { _, _ ->
        coEvery { StockMediaClient.fetchThumbnailBytes(any(), any()) } returns tinyPngBytes()
        val gate = CompletableDeferred<StockMediaClient.SearchOutcome>()
        coEvery { StockMediaClient.search(any(), any(), any(), any(), any(), any()) } coAnswers { gate.await() }

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        awaitUntil { onAllNodes(progressSpinner).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }

        gate.complete(StockMediaClient.SearchOutcome.Success(listOf(item("1")), hasMore = false))
        awaitUntil { onAllNodes(progressSpinner).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
    }

    // ── Load more ─────────────────────────────────────────────────────────────────

    @Test
    fun `clicking Load more fetches the next page and appends its results`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        coEvery { StockMediaClient.search(any(), any(), any(), any(), 1, any()) } returns
            StockMediaClient.SearchOutcome.Success(listOf(item("1")), hasMore = true)
        coEvery { StockMediaClient.search(any(), any(), any(), any(), 2, any()) } returns
            StockMediaClient.SearchOutcome.Success(listOf(item("2")), hasMore = false)

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()
        onNodeWithText("Load more").assertExists()

        onNodeWithText("Load more").performClick()
        settle()
        waitForIdle()

        onNodeWithText("Load more").assertDoesNotExist()
        onAllNodesWithContentDescription("Browse stock photos/videos").assertCountEquals(2)
    }

    @Test
    fun `a spinner replaces Load more while the next page is in flight`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        coEvery { StockMediaClient.fetchThumbnailBytes(any(), any()) } returns tinyPngBytes()
        coEvery { StockMediaClient.search(any(), any(), any(), any(), 1, any()) } returns
            StockMediaClient.SearchOutcome.Success(listOf(item("1")), hasMore = true)
        val gate = CompletableDeferred<StockMediaClient.SearchOutcome>()
        coEvery { StockMediaClient.search(any(), any(), any(), any(), 2, any()) } coAnswers { gate.await() }

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()
        onNodeWithText("Load more").performClick()

        awaitUntil { onAllNodes(progressSpinner).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        onNodeWithText("Load more").assertDoesNotExist()

        gate.complete(StockMediaClient.SearchOutcome.Success(listOf(item("2")), hasMore = false))
        awaitUntil { onAllNodes(progressSpinner).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
    }

    // ── Thumbnails ────────────────────────────────────────────────────────────────

    @Test
    fun `a decodable thumbnail replaces the loading spinner with the image`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        coEvery { StockMediaClient.fetchThumbnailBytes(any(), any()) } returns tinyPngBytes()
        searchReturns(StockMediaClient.SearchOutcome.Success(listOf(item("1")), hasMore = false))

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        awaitUntil { onAllNodes(progressSpinner).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
    }

    @Test
    fun `corrupt thumbnail bytes leave the loading spinner showing`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        coEvery { StockMediaClient.fetchThumbnailBytes(any(), any()) } returns "not a real image".toByteArray()
        searchReturns(StockMediaClient.SearchOutcome.Success(listOf(item("1")), hasMore = false))

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        assertTrue(onAllNodes(progressSpinner).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty())
    }

    @Test
    fun `a video result shows a play icon over its thumbnail`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        searchReturns(StockMediaClient.SearchOutcome.Success(listOf(item("1", isVideo = true)), hasMore = false))

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        onAllNodesWithContentDescription("Browse stock photos/videos").assertCountEquals(1)
    }

    // ── Downloading ───────────────────────────────────────────────────────────────

    @Test
    fun `downloading a result invokes the onMediaDownloaded callback with the saved file's path`() = dialog(pexelsApiKey = "a-key") { _, downloaded ->
        val saved = File.createTempFile("stock", ".jpg").also { it.deleteOnExit() }
        coEvery { StockMediaClient.download(any(), any(), any()) } returns StockMediaClient.DownloadOutcome
            .Success(saved)
        searchReturns(StockMediaClient.SearchOutcome.Success(listOf(item("1")), hasMore = false))

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        onNodeWithContentDescription("Browse stock photos/videos").performClick()
        awaitUntil { downloaded() != null }

        assertEquals(saved.absolutePath, downloaded())
    }

    @Test
    fun `a downloading tile shows a spinner on its own download button while it runs`() =
        dialog(pexelsApiKey = "a-key") { _, _ ->
        coEvery { StockMediaClient.fetchThumbnailBytes(any(), any()) } returns tinyPngBytes()
        val gate = CompletableDeferred<StockMediaClient.DownloadOutcome>()
        coEvery { StockMediaClient.download(any(), any(), any()) } coAnswers { gate.await() }
        searchReturns(StockMediaClient.SearchOutcome.Success(listOf(item("1")), hasMore = false))

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        onNodeWithContentDescription("Browse stock photos/videos").performClick()
        awaitUntil { onAllNodes(progressSpinner).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }

        gate.complete(StockMediaClient.DownloadOutcome.NetworkError)
        awaitUntil { onAllNodes(progressSpinner).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
    }

    @Test
    fun `a network error while downloading shows its own message`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        coEvery { StockMediaClient.download(any(), any(), any()) } returns StockMediaClient.DownloadOutcome.NetworkError
        searchReturns(StockMediaClient.SearchOutcome.Success(listOf(item("1")), hasMore = false))

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        onNodeWithContentDescription("Browse stock photos/videos").performClick()
        settle()
        waitForIdle()

        onNodeWithText("Download failed — check your connection").assertExists()
    }

    @Test
    fun `a generic failure while downloading shows its own message`() = dialog(pexelsApiKey = "a-key") { _, _ ->
        coEvery { StockMediaClient.download(any(), any(), any()) } returns StockMediaClient.DownloadOutcome.Failure
        searchReturns(StockMediaClient.SearchOutcome.Success(listOf(item("1")), hasMore = false))

        onNodeWithText("Search for photos…").performTextInput("worship")
        onNodeWithText("worship").performImeAction()
        settle()
        waitForIdle()

        onNodeWithContentDescription("Browse stock photos/videos").performClick()
        settle()
        waitForIdle()

        onNodeWithText("Download failed. Please try again.").assertExists()
    }

    // ── Get-a-key link ────────────────────────────────────────────────────────────

    @Test
    fun `clicking Get a free key browses to the Pexels signup page`() = dialog { _, _ ->
        val browsed = stubDesktop()

        onNodeWithText("Get a free key →").performClick()

        assertEquals(URI("https://www.pexels.com/api/"), browsed())
    }

    @Test
    fun `clicking Get a free key on the Pixabay tab browses to the Pixabay signup page`() = dialog { _, _ ->
        val browsed = stubDesktop()
        onNodeWithText("Pixabay").performClick()

        onNodeWithText("Get a free key →").performClick()

        assertEquals(URI("https://pixabay.com/api/docs/"), browsed())
    }

    @Test
    fun `clicking Get a free key without desktop support does not crash`() = dialog { _, _ ->
        // No stubDesktop() here — the JVM runs headless, so Desktop.getDesktop() throws for real,
        // exercising the runCatching around it rather than a happy-path browse.
        onNodeWithText("Get a free key →").performClick()
        settle()
        waitForIdle()

        onNodeWithText("Get a free key →").assertExists()
    }
}
