@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.media.data.StockMediaClient
import org.churchpresenter.media.dialogs.StockMediaBrowserDialogContent
import org.churchpresenter.media.viewmodel.StockMediaViewModel
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.stock_photo_browse_photos_title
import org.churchpresenter.strings.generated.resources.stock_photo_search_placeholder_photo
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The stock search the Background tab's image row opens, shot through its `…Content` composable.
 *
 * **The result grid is not shot.** Its items only arrive from a live Pexels or Pixabay query —
 * `StockMediaViewModel` fills them itself from a client it constructs, with no seam to hand it a fixed
 * page — so an image of results would need the network. What *is* shot is everything reachable
 * without it: the key prompt an operator meets first, and the empty state once a key is saved.
 */
class StockMediaBrowserScreenshotTest {

    private val models = mutableListOf<StockMediaViewModel>()

    @AfterTest
    fun cleanUp() {
        models.forEach { runCatching { it.dispose() } }
        models.clear()
    }

    /** No key saved: the dialog asks for one rather than searching. */
    @Test
    fun `the stock browser with no API key`() = stock("stock_no_key", pexelsKey = "", pixabayKey = "")

    /** A key saved, nothing searched for yet. */
    @Test
    fun `the stock browser ready to search`() =
        stock("stock_ready", pexelsKey = "pexels-key", pixabayKey = "pixabay-key")

    /** The second source's tab, which carries its own key. */
    @Test
    fun `the stock browser's second source`() =
        stock("stock_second_source", pexelsKey = "pexels-key", pixabayKey = "", selectedTab = 1)

    private fun stock(
        name: String,
        pexelsKey: String,
        pixabayKey: String,
        selectedTab: Int = 0,
    ) = stackedThemes(SECTION, name) { mode, file ->
        val pexels = viewModel(StockMediaClient.StockSource.PEXELS)
        val pixabay = viewModel(StockMediaClient.StockSource.PIXABAY)
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            var tab by remember { mutableStateOf(selectedTab) }
                            StockMediaBrowserDialogContent(
                                titleRes = Res.string.stock_photo_browse_photos_title,
                                searchPlaceholderRes = Res.string.stock_photo_search_placeholder_photo,
                                pexelsViewModel = pexels,
                                pixabayViewModel = pixabay,
                                pexelsApiKey = pexelsKey,
                                onPexelsApiKeyChange = {},
                                pixabayApiKey = pixabayKey,
                                onPixabayApiKeyChange = {},
                                selectedTab = tab,
                                onSelectedTabChange = { tab = it },
                                onDismiss = {},
                                onDownloadedAndClose = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()
            captureTo(file)
        }
    }

    private fun viewModel(source: StockMediaClient.StockSource) =
        StockMediaViewModel(StockMediaClient.StockMediaType.PHOTO, source).also { models += it }

    private companion object {
        const val SECTION = "stockMediaBrowser"
    }
}
