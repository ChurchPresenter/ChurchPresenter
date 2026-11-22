package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import org.churchpresenter.bibletab.BibleViewModel
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class RemoteSlideCacheMissTest {

    private val dir: File = Files.createTempDirectory("cp-remote-slide-miss").toFile()
    private val pictures = PicturesViewModel()
    private val presentations = PresentationViewModel()
    private val bible = BibleViewModel(
        AppSettings(),
        dispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
    )
    private val presenter = PresenterManager()

    @AfterTest
    fun cleanUp() {
        runCatching { pictures.dispose() }
        runCatching { bible.dispose() }
        dir.deleteRecursively()
    }

    @Test
    fun `a slide whose file is gone from the cache leaves the output alone`() = runComposeUiTest {
        presentations.slideFiles.addAll(listOf(File(dir, "gone-1.png"), File(dir, "gone-2.png")))
        // A cold flow's emit returns once the collector has finished with the value.
        var handled = false
        val selectSlide = flow {
            emit("deck" to 1)
            handled = true
        }
        setContent {
            RemoteCommandEffects(
                appSettings = AppSettings(),
                picturesViewModel = pictures,
                presentationViewModel = presentations,
                bibleViewModel = bible,
                presenterManager = presenter,
                resolveImageFile = null,
                onSettingsChange = {},
                onSongItemSelected = {},
                onPictureItemSelected = {},
                onPresentationItemSelected = {},
                onMediaItemSelected = {},
                onSelectTab = {},
                pushCurrentSlideIfLive = {},
                selectSlideFlow = selectSlide,
            )
        }
        waitUntil("the selection was handled", timeoutMillis = 5_000) { handled }

        assertEquals(1, presentations.selectedSlideIndex, "the deck still moves to the slide")
        assertNull(presenter.selectedSlide.value)
        assertNull(presenter.liveSlide.value)
        assertEquals(Presenting.NONE, presenter.slideContent.value)
    }
}
