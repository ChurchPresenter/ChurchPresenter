package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import java.io.File

/** The content that goes through Preview while preview mode is on; everything else still goes straight to air. */
internal val CUEABLE_MODES =
    setOf(Presenting.BIBLE, Presenting.PICTURES, Presenting.LYRICS, Presenting.LOWER_THIRD, Presenting.ANNOUNCEMENTS)

/**
 * The Preview bus: what is cued, not yet on air, and Take, which puts it on air.
 *
 * Preview is a second [PresenterManager], [manager], so it is drawn by the same renderer as every
 * output and can never show something Program would draw differently. With preview mode off --
 * the default -- nothing reaches it and every go-live goes straight to [program], as it always did.
 *
 * With it on, a **new item** goes to Preview and waits for [take]; **stepping** within the item
 * already on Program (the next verse of the chapter, the next section of the song, the next picture
 * in the folder) still goes straight to air. Only
 * [CUEABLE_MODES] are cued; the rest still go live directly.
 */
class PreviewBus internal constructor(private val program: PresenterManager) {

    /** What Preview shows. Nothing on air reads it. */
    val manager: PresenterManager by lazy { PresenterManager(showPresenterWindowInitially = false) }

    private val _enabled = mutableStateOf(false)

    /** Whether preview mode is on. */
    val enabled: State<Boolean> = _enabled

    /** Turns preview mode on or off; off empties Preview. */
    fun setEnabled(on: Boolean) {
        if (_enabled.value == on) return
        _enabled.value = on
        if (!on) clear()
    }

    /** Whether [mode] is cued on Preview, waiting for [take]. */
    fun isCued(mode: Presenting): Boolean = _enabled.value && manager.isLive(mode)

    /** Whether anything is cued. */
    val anythingCued: Boolean get() = _enabled.value && manager.anythingLive

    /**
     * Where a new item of [mode] goes: Preview while preview mode is on and [mode] is one of
     * [CUEABLE_MODES], else Program.
     */
    internal fun forNewItem(mode: Presenting): PresenterManager =
        if (_enabled.value && mode in CUEABLE_MODES) manager else program

    /**
     * A go-live of [mode] whose content comes separately (a schedule row, a tab's Go Live): cued on
     * Preview when it is a new item, else on Program as before. Already cued, or [mode] already on
     * Program while its content is being stepped, it changes nothing here.
     */
    fun present(mode: Presenting) {
        when {
            forNewItem(mode) === program -> program.setPresentingMode(mode)
            // A timer's text ticks on air; its go-live goes there with it.
            mode == Presenting.ANNOUNCEMENTS && program.announcementTickerLive.value -> program.setPresentingMode(mode)
            manager.isLive(mode) -> Unit
            mode == Presenting.PICTURES && program.isLive(mode) -> Unit
            // The song on air going live again -- a section of it double-clicked -- is a step.
            mode == Presenting.LYRICS && songTarget === program -> program.setPresentingMode(mode)
            mode == Presenting.BIBLE && verseTarget === program -> program.setPresentingMode(mode)
            else -> manager.setPresentingMode(mode)
        }
    }

    /** Where a picture goes: see [present]. A picture from the folder already on air is a step. */
    internal fun forPicture(path: String?): PresenterManager = when {
        forNewItem(Presenting.PICTURES) === program -> program
        manager.isLive(Presenting.PICTURES) -> manager
        program.isLive(Presenting.PICTURES) && sameFolder(path, program.selectedImagePath.value) -> program
        else -> manager
    }

    /** Where the last push of verses went -- see [forVerses]. */
    private var verseTarget: PresenterManager? = null

    /**
     * Where a push of [verses] goes. The Bible tab pushes whatever is selected, live or not: a
     * verse of the chapter on air is a step and goes there, and any other passage goes to Preview,
     * where nothing shows it until it is cued -- the line the tab's own auto-hold draws.
     */
    internal fun forVerses(verses: List<SelectedVerse>): PresenterManager {
        val target = when {
            forNewItem(Presenting.BIBLE) === program -> program
            program.isLive(Presenting.BIBLE) &&
                chapterOf(verses.firstOrNull()) == chapterOf(program.selectedVerses.value.firstOrNull()) -> program
            else -> manager
        }
        verseTarget = target
        return target
    }

    /** Where the last push of a song went -- see [forSong]. */
    private var songTarget: PresenterManager? = null

    /**
     * Where a push of [section]'s song goes. The Songs tab pushes whatever is selected, live or
     * not: the song on air is stepped there, and any other song goes to Preview, where nothing
     * shows it until it is cued.
     */
    internal fun forSong(section: LyricSection?): PresenterManager {
        val target = when {
            forNewItem(Presenting.LYRICS) === program -> program
            program.isLive(Presenting.LYRICS) && songOf(section) == songOf(program.lyricSection.value) -> program
            else -> manager
        }
        songTarget = target
        return target
    }

    /** Where a song's section and line indexes go: wherever its sections went. */
    internal val songStepTarget: PresenterManager
        get() = songTarget?.takeIf { _enabled.value } ?: program

    /** Puts a lower third up, or cues it while preview mode is on. */
    fun showLowerThird(json: String, pauseAtFrame: Boolean, pauseFrame: Float, pauseDurationMs: Long, name: String) {
        val target = forNewItem(Presenting.LOWER_THIRD)
        target.setLottieContent(json, pauseAtFrame, pauseFrame, pauseDurationMs, name)
        target.setPresentingMode(Presenting.LOWER_THIRD)
        program.setShowPresenterWindow(true)
    }

    /**
     * Puts what is cued on air -- the slide first, as going live with one takes Program's overlays
     * down, then each overlay in the order it was cued -- and empties Preview.
     */
    fun take() {
        if (!anythingCued) return
        val slide = manager.presentingMode.value
        if (slide != Presenting.NONE) putOnAir(slide, from = manager, to = program)
        manager.overlays.value.forEach { putOnAir(it, from = manager, to = program) }
        // What was taken is on air now, so its next verse or section is a step there.
        if (slide == Presenting.BIBLE) verseTarget = program
        if (slide == Presenting.LYRICS) songTarget = program
        program.setShowPresenterWindow(true)
        clear()
    }

    /** Empties Preview. */
    fun clear() {
        if (manager.anythingLive) manager.setPresentingMode(Presenting.NONE)
    }
}

/** What [from] holds of [mode], put on [to] and taken live there. */
private fun putOnAir(mode: Presenting, from: PresenterManager, to: PresenterManager) {
    when (mode) {
        Presenting.PICTURES -> {
            to.setSelectedImagePath(from.selectedImagePath.value)
            to.setNextImagePath(from.nextImagePath.value)
        }
        Presenting.LOWER_THIRD -> to.setLottieContent(
            from.lottieJsonContent.value,
            from.lottiePauseAtFrame.value,
            from.lottiePauseFrame.value,
            from.lottiePauseDurationMs.value,
            from.currentLowerThirdName.value,
        )
        Presenting.ANNOUNCEMENTS -> to.setAnnouncementText(from.announcementText.value)
        Presenting.BIBLE -> {
            // A hold the tab put on air while another chapter was browsed must not keep the
            // taken passage off it.
            to.setBibleHold(false)
            to.setSelectedVerses(from.selectedVerses.value)
        }
        Presenting.LYRICS -> {
            to.setAllLyricSections(from.allLyricSections.value)
            to.setSongDisplaySectionIndex(from.songDisplaySectionIndex.value)
            to.setSongDisplayLineIndex(from.songDisplayLineIndex.value)
            to.setLyricSection(from.lyricSection.value)
        }
        else -> return
    }
    to.setPresentingMode(mode)
}

/** Which chapter [verse] is from, as far as telling one passage from another goes. */
private fun chapterOf(verse: SelectedVerse?): Pair<String, Int>? = verse?.let { it.bookName to it.chapter }

/** Which song [section] is from, as far as telling one song from another goes. */
private fun songOf(section: LyricSection?): Pair<Int, String>? = section?.let { it.songNumber to it.title }

private fun sameFolder(a: String?, b: String?): Boolean =
    a != null && b != null && File(a).parentFile == File(b).parentFile

/** [this] with preview mode turned [on] or off. */
internal fun AppSettings.withPreviewMode(on: Boolean): AppSettings =
    copy(projectionSettings = projectionSettings.copy(previewModeEnabled = on))
