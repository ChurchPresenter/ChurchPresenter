package org.churchpresenter.songs

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import org.churchpresenter.core.models.songs.MAX_SONG_EXTRA_TRANSLATIONS
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.churchpresenter.core.models.songs.SONG_BACKGROUND_PREFIX
import org.churchpresenter.core.models.songs.SONG_LOWER_THIRD_BACKGROUND_PREFIX
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTranslation
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.songchords.ChordTransposer

private const val BPM_MAX_DIGITS = 3
private const val CAPO_MAX_DIGITS = 2
private const val MAX_BPM = 300
private const val MAX_CAPO = 12

/**
 * One language of the song as it is being typed.
 *
 * The lyrics are a [TextFieldValue] rather than a `String` because the box needs to keep its
 * selection and cursor across a pane switch — going to another language and back must not put the
 * caret at the top.
 */
internal data class TranslationDraft(
    val label: String = "",
    val title: String = "",
    val lyrics: TextFieldValue = TextFieldValue(""),
) {
    /**
     * The saved form. Lyrics that are nothing but section headers count as empty, which is what
     * stops an untouched pane — the editor pre-fills none, but a template might — from being
     * written out as a language the song does not actually have.
     */
    fun toTranslation(): SongTranslation {
        val lines = lyrics.text.split("\n")
        // Structure alone -- headers, slide breaks, background directives -- is not a translation.
        // A lyric line that opens on a chord (`[G]Blagodat`) is words, not a header.
        val blank = lines.all {
            it.isBlank() || ChordTransposer.isSectionHeader(it) ||
                ChordTransposer.isSlideBreak(it) || ChordTransposer.isBackgroundDirective(it)
        }
        return SongTranslation(
            label = label.trim(),
            title = title.trim(),
            lyrics = if (blank) emptyList() else lines,
        )
    }
}

/**
 * Everything the song editor holds while a song is open, and the edits it makes to it.
 *
 * Remembered against the song and the dialog's visibility, so reopening the dialog over the same
 * song discards an abandoned edit rather than resuming it. Every field is snapshot state, so the
 * editor's pieces recompose when one changes.
 */
@Stable
internal class EditSongState(private val song: SongItem) {
    /** Assigned on every composition: a new song's title follows its first lyric line until typed. */
    var isNewSong: Boolean = false

    // Filter out non-digits from song number (handles cases like "3.1" -> "3" or "31")
    var number by mutableStateOf(song.number.filter { it.isDigit() })
    var title by mutableStateOf(song.title)
        private set
    private var titleManuallyEdited by mutableStateOf(song.title.isNotBlank())
    var songbook by mutableStateOf(song.songbook)
    var tune by mutableStateOf(song.tune)
    var author by mutableStateOf(song.author)
    var composer by mutableStateOf(song.composer)
    var ccli by mutableStateOf(song.ccliNumber)
    var lyrics by mutableStateOf(TextFieldValue(song.lyrics.joinToString("\n")))

    // One draft per language beside the primary, always [MAX_SONG_EXTRA_TRANSLATIONS] of them so a
    // pane's state does not move when a language before it is emptied. Which ones are *shown* is
    // [visibleTranslations] below.
    var translations by mutableStateOf(
        List(MAX_SONG_EXTRA_TRANSLATIONS) { index ->
            val translation = song.extraTranslations().getOrNull(index)
            TranslationDraft(
                label = translation?.label.orEmpty(),
                title = translation?.title.orEmpty(),
                lyrics = TextFieldValue(translation?.lyrics?.joinToString("\n").orEmpty()),
            )
        }
    )
        private set

    // How many extra languages have a tab. Always at least one, so a monolingual song still opens
    // with the Secondary pane it has always had; more when the song already carries them.
    var visibleTranslations by mutableStateOf(song.extraTranslations().size.coerceIn(1, MAX_SONG_EXTRA_TRANSLATIONS))
        private set

    var background by mutableStateOf(song.background)
        private set
    var lowerThirdBackground by mutableStateOf(song.lowerThirdBackground)
        private set
    var backgroundPanelOpen by mutableStateOf(false)

    // 0 is the song's own background; 1.. are its sections, in the order they are written. Held as
    // an index rather than a name because two sections may share one, and clamped on every read
    // because the sections come from the lyrics box and can be deleted while the panel is open.
    var backgroundScope by mutableStateOf(0)

    // 0 is the primary; 1.. are the extra languages, in the order they are written.
    var pane by mutableStateOf(0)

    // The key the chord palette offers, read from the song once as it opens. Never guessed again
    // from later edits: inserting a chord ahead of the first one must not move it.
    var songKey by mutableStateOf(ChordTransposer.detectKey(lyrics.text))
        private set

    // How far the song's chords have been rewritten this session, so reset can take them back.
    var transposed by mutableStateOf(0)
        private set
    private var transposeRecorded = false

    /** The song book the song had when it was opened, which cancelling a new book goes back to. */
    val originalSongbook: String get() = song.songbook

    /** The lyrics the open pane shows. */
    val paneValue: TextFieldValue get() = if (pane == 0) lyrics else translations[pane - 1].lyrics

    fun editTitle(value: String) {
        title = value
        titleManuallyEdited = value.isNotBlank()
    }

    fun editTranslationTitle(slot: Int, value: String) {
        translations = translations.mapIndexed { index, draft ->
            if (index == slot) draft.copy(title = value) else draft
        }
    }

    /**
     * What extra language [index] is called, for its pane tab and its title card alike: the
     * install-wide name in [names] first, and a label the song file carries itself (an import's)
     * only while the language has none. Read live, so a rename typed here relabels both at once.
     */
    fun languageName(index: Int, names: List<String>): String =
        names[index + 1].trim().ifBlank { translations[index].label }

    /** Opens a tab for one more language and switches to it. */
    fun addTranslationPane() {
        visibleTranslations += 1
        pane = visibleTranslations
    }

    /** Moves the chord palette's key by [delta] semitones without touching the lyrics. */
    fun stepKey(delta: Int) {
        val pitch = (ChordTransposer.pitchOf(songKey) ?: 0) + delta
        songKey = ChordTransposer.nameOf(pitch, ChordTransposer.prefersFlats(pitch))
    }

    // Rewrites the chords of every language at once — a translation is sung to the same chords —
    // and moves the palette's key with them.
    fun transposeBy(delta: Int) {
        val pitch = (ChordTransposer.pitchOf(songKey) ?: 0) + delta
        val flats = ChordTransposer.prefersFlats(pitch)
        lyrics = transposeValue(lyrics, delta, flats)
        translations = translations.map { it.copy(lyrics = transposeValue(it.lyrics, delta, flats)) }
        songKey = ChordTransposer.nameOf(pitch, flats)
        transposed += delta
        if (!transposeRecorded) {
            transposeRecorded = true
            UsageEvents.record(UsageEvent.SONG_TRANSPOSED)
        }
    }

    fun setPaneValue(value: TextFieldValue) {
        if (pane > 0) {
            translations = translations.mapIndexed { index, draft ->
                if (index == pane - 1) draft.copy(lyrics = value) else draft
            }
            return
        }
        lyrics = value
        // Auto-fill title from first non-header, non-blank lyric line
        if (isNewSong && !titleManuallyEdited) {
            title = value.text.lines()
                .firstOrNull { it.isNotBlank() && !it.trim().startsWith("[") }
                ?.trim()
                ?: ""
        }
    }

    /**
     * Sets the background -- or with [lowerThird] the lower-third background -- of [scope]: the
     * song's own at 0, otherwise the section at `scope - 1`, which is written into the lyrics.
     */
    fun setBackground(scope: Int, next: SongBackground, lowerThird: Boolean) {
        when {
            scope != 0 -> lyrics = lyrics.withSectionBackgroundAt(
                scope - 1,
                if (lowerThird) SONG_LOWER_THIRD_BACKGROUND_PREFIX else SONG_BACKGROUND_PREFIX,
                next,
            )
            lowerThird -> lowerThirdBackground = next
            else -> background = next
        }
    }

    /** Whether another song in [existingSongs] already has this number, title and song book. */
    fun isDuplicateIn(existingSongs: List<SongItem>): Boolean =
        if (title.isBlank() || songbook.isBlank()) false
        else existingSongs.any {
            it.number == number &&
                it.title.equals(title, ignoreCase = true) &&
                it.songbook.equals(songbook, ignoreCase = true) &&
                it.sourceFile != song.sourceFile
        }

    /** The song rebuilt from what has been typed. */
    fun toSongItem(): SongItem = SongItem(
        number = number,
        title = title,
        songbook = songbook,
        tune = tune,
        author = author,
        composer = composer,
        lyrics = lyrics.text.split("\n"),
        sourceFile = song.sourceFile,
        ccliNumber = ccli,
        background = background,
        lowerThirdBackground = lowerThirdBackground,
    ).withTranslations(translations.map { it.toTranslation() })
}

/**
 * Tempo and capo as they are being typed: blank rather than "0" when unset, since 0 is the off
 * value and an empty box reads as "not set". Remembered against the stored tuning as well as the
 * song, so a tuning saved elsewhere refreshes the boxes.
 */
@Stable
internal class EditSongTuning(tuning: SongTuning) {
    var bpm by mutableStateOf(if (tuning.bpm > 0) tuning.bpm.toString() else "")
        private set
    var capo by mutableStateOf(if (tuning.capo > 0) tuning.capo.toString() else "")
        private set

    fun editBpm(value: String) {
        bpm = value.filter { it.isDigit() }.take(BPM_MAX_DIGITS)
    }

    fun editCapo(value: String) {
        capo = value.filter { it.isDigit() }.take(CAPO_MAX_DIGITS)
    }

    fun toSongTuning(): SongTuning = SongTuning(
        bpm = bpm.toIntOrNull()?.coerceIn(0, MAX_BPM) ?: 0,
        capo = capo.toIntOrNull()?.coerceIn(0, MAX_CAPO) ?: 0,
    )
}

/**
 * The install-wide language names, one per pane, edited here and stored only on Save. [stored] is
 * what they were when the editor opened.
 */
@Stable
internal class SongLanguageNames(private val stored: List<String>) {
    var names by mutableStateOf(List(MAX_SONG_TRANSLATIONS) { stored.getOrElse(it) { "" } })
        private set

    fun rename(pane: Int, value: String) {
        names = names.mapIndexed { index, old -> if (index == pane) value else old }
    }

    /** The trimmed names when they differ from the stored ones, or null when nothing was renamed. */
    fun changed(): List<String>? {
        val trimmed = names.map { it.trim() }
        return trimmed.takeIf { it != List(MAX_SONG_TRANSLATIONS) { stored.getOrElse(it) { "" }.trim() } }
    }
}
