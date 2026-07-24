package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.every
import io.mockk.mockk
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.PresentationSettings
import org.churchpresenter.app.churchpresenter.models.AnimationType
import presentation.engine.model.Deck
import presentation.engine.model.DeckFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The runtime-adjustable playback settings on [PresentationViewModel] — seeded from
 * [PresentationSettings] and overridable live from the settings UI while a deck is open.
 *
 * These need no disk or rendering: the ViewModel holds them as plain observable state, so they are
 * set and read directly.
 */
class PresentationViewModelSettingsTest {

    private fun viewModel(settings: PresentationSettings = PresentationSettings()) =
        PresentationViewModel(AppSettings(presentationSettings = settings))

    @Test
    fun `the transition duration is overridable at runtime`() {
        val vm = viewModel(PresentationSettings(transitionDuration = 500f))
        assertEquals(500f, vm.transitionDuration, "it starts at the configured default")

        vm.transitionDuration = 900f

        assertEquals(900f, vm.transitionDuration, "the settings UI can change it live while a deck is open")
    }

    @Test
    fun `the auto-scroll interval, looping and animation type are overridable at runtime`() {
        val vm = viewModel()

        vm.autoScrollInterval = 12f
        vm.isLooping = false
        vm.animationType = AnimationType.SLIDE_RIGHT

        assertEquals(12f, vm.autoScrollInterval)
        assertEquals(false, vm.isLooping)
        assertEquals(AnimationType.SLIDE_RIGHT, vm.animationType)
    }

    // ── exposableDeck: whether a deck is handed to the animated player ───────────
    //
    // exposableDeck is private, so it is reached by reflection (as CrashReporterTest does for
    // scrubPii). A mock Deck only needs its `format` stubbed; the decision is a pure function of
    // that plus the "Animate Keynote" setting.

    private fun deck(format: DeckFormat): Deck = mockk { every { this@mockk.format } returns format }

    private fun PresentationViewModel.exposableDeck(deck: Deck?): Deck? =
        PresentationViewModel::class.java
            .getDeclaredMethod("exposableDeck", Deck::class.java)
            .apply { isAccessible = true }
            .invoke(this, deck) as Deck?

    @Test
    fun `a keynote deck is withheld from playback when Animate Keynote is off`() {
        // Keynote animation rides on a reverse-engineered parser, so with the setting off the .key
        // deck stays on the static JPEG path — the animated player must never receive it.
        val vm = viewModel(PresentationSettings(animateKeynote = false))
        val keynote = deck(DeckFormat.KEYNOTE)

        assertNull(vm.exposableDeck(keynote), "the animated player must not start on a Keynote deck when the setting is off")
    }

    @Test
    fun `a keynote deck is exposed when Animate Keynote is on`() {
        val vm = viewModel(PresentationSettings(animateKeynote = true))
        val keynote = deck(DeckFormat.KEYNOTE)

        assertSame(keynote, vm.exposableDeck(keynote))
    }

    @Test
    fun `a non-keynote deck is exposed regardless of the Animate Keynote setting`() {
        val pptx = deck(DeckFormat.PPTX)

        assertSame(pptx, viewModel(PresentationSettings(animateKeynote = false)).exposableDeck(pptx))
        assertSame(pptx, viewModel(PresentationSettings(animateKeynote = true)).exposableDeck(pptx))
    }

    @Test
    fun `no deck exposes nothing`() {
        assertNull(viewModel().exposableDeck(null))
    }
}
