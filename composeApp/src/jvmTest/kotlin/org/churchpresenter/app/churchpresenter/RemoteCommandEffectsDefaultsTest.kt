package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bibletab.BibleViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [RemoteCommandEffects] as a build with no remote wired calls it: every flow and the statistics
 * left at their defaults. Nothing is collected, so nothing reaches the tabs or the output.
 */
@OptIn(ExperimentalTestApi::class)
class RemoteCommandEffectsDefaultsTest {

    @Test
    fun `with no remote wired the effects collect nothing and leave the output alone`() = runComposeUiTest {
        val pictures = PicturesViewModel()
        val bible = BibleViewModel(
            AppSettings(),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val presenter = PresenterManager()
        val calls = mutableListOf<String>()
        try {
            setContent {
                RemoteCommandEffects(
                    appSettings = AppSettings(),
                    picturesViewModel = pictures,
                    presentationViewModel = PresentationViewModel(),
                    bibleViewModel = bible,
                    presenterManager = presenter,
                    resolveImageFile = null,
                    onSettingsChange = { calls += "settings" },
                    onSongItemSelected = { calls += "song" },
                    onPictureItemSelected = { calls += "picture" },
                    onPresentationItemSelected = { calls += "presentation" },
                    onMediaItemSelected = { calls += "media" },
                    onSelectTab = { calls += "tab" },
                    pushCurrentSlideIfLive = { calls += "push" },
                )
            }
            waitForIdle()
            assertTrue(calls.isEmpty(), "nothing was asked for: $calls")
            assertEquals(Presenting.NONE, presenter.slideContent.value)
        } finally {
            pictures.dispose()
            bible.dispose()
        }
    }
}
