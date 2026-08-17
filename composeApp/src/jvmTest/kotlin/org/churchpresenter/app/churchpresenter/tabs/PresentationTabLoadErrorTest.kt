@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import org.churchpresenter.app.churchpresenter.viewmodel.PresentationViewModel
import presentation.engine.LoadResult
import presentation.engine.model.DeckLoadError
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PresentationTabLoadErrorTest {

    private lateinit var dir: File

    @AfterTest
    fun cleanUp() {
        if (::dir.isInitialized) dir.deleteRecursively()
    }

    /** An empty file with a supported extension — its bytes are never parsed since [loadDeck] is stubbed. */
    private fun presentationFile(): File {
        dir = Files.createTempDirectory("cp-presentation-tab-error").toFile()
        return File(dir, "deck.pdf").apply { writeText("") }
    }

    private fun ComposeUiTest.awaitError(vm: PresentationViewModel, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!vm.isLoading && vm.loadError != null) {
                waitForIdle()
                return
            }
            Thread.sleep(20)
        }
        throw AssertionError("timed out waiting for the failed load to report")
    }

    private fun withLoadError(error: DeckLoadError, block: ComposeUiTest.(vm: PresentationViewModel) -> Unit) =
        presentationTab { vm, _ ->
            vm.loadDeck = { LoadResult.Failure(error) }
            vm.addPresentation(presentationFile())
            awaitError(vm)
            block(vm)
        }

    @Test
    fun `a password-protected deck shows the password error and no slides`() =
        withLoadError(DeckLoadError.PASSWORD_PROTECTED) { vm ->
        assertTrue(
            showsContainingText(
                "This PDF is password-protected and can't be opened. Remove the password and try again.",
            ),
            renderedText().toString(),
        )
        assertTrue(vm.slideFiles.isEmpty())
    }

    @Test
    fun `an empty document shows the empty-document error`() = withLoadError(DeckLoadError.EMPTY_DOCUMENT) { _ ->
        assertTrue(showsContainingText("This file has no pages to display."), renderedText().toString())
    }

    @Test
    fun `an unsupported format shows the generic render-failed error`() =
        withLoadError(DeckLoadError.UNSUPPORTED_FORMAT) { _ ->
        assertTrue(
            showsContainingText("Couldn't read this file — it may be corrupted or in an unsupported format."),
            renderedText().toString(),
        )
    }

    @Test
    fun `a parse failure shows the generic render-failed error too`() = withLoadError(DeckLoadError.PARSE_FAILED) { _ ->
        assertTrue(showsContainingText("Couldn't read this file — it may be corrupted or in an unsupported format."))
    }
}
