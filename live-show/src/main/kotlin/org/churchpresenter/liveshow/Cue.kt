package org.churchpresenter.liveshow

import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongBackground

/**
 * What one layer shows: a typed value naming its content. Two cues that are equal show the same
 * thing, which is how a layer knows an incoming cue changes nothing.
 */
sealed interface Cue {
    val layer: Layer

    /**
     * What sits under everything: the background [source]'s setting names, or [own] when the
     * content carries one (a song's). Each output resolves it against its own settings, so one cue
     * can be a picture on the projector and transparent on a stream key.
     */
    data class Background(val source: BackgroundSource, val own: SongBackground = SongBackground()) : Cue {
        override val layer get() = Layer.BACKGROUND
    }

    /** A video or stream, full frame. */
    data class Video(val url: String) : Cue {
        override val layer get() = Layer.MEDIA
    }

    /** One picture of a slideshow, full frame; null while the first is still on its way. */
    data class Picture(val path: String?) : Cue {
        override val layer get() = Layer.MEDIA
    }

    /** Bible verses, primary and secondary language together. */
    data class Verses(val verses: List<SelectedVerse>) : Cue {
        override val layer get() = Layer.SLIDE
    }

    /** One section of a song, and where in the song it is. */
    data class Song(val section: LyricSection, val sectionIndex: Int, val lineIndex: Int) : Cue {
        override val layer get() = Layer.SLIDE
    }

    /**
     * One slide of a presentation; [fileName] is null for a deck that has none, and [index] is -1
     * while no slide has been shown yet.
     */
    data class PresentationSlide(val fileName: String?, val index: Int) : Cue {
        override val layer get() = Layer.SLIDE
    }

    /** A canvas scene; null while none is chosen. */
    data class SceneCue(val scene: Scene?) : Cue {
        override val layer get() = Layer.SLIDE
    }

    /** A web page. */
    data class Web(val url: String) : Cue {
        override val layer get() = Layer.SLIDE
    }

    /** A Q&A question; null while the QR code is up without one. */
    data class QuestionCue(val question: Question?) : Cue {
        override val layer get() = Layer.SLIDE
    }

    /** A Strong's dictionary entry, by its number; null while none is chosen. */
    data class Dictionary(val number: String?) : Cue {
        override val layer get() = Layer.SLIDE
    }

    /** Live transcription. Its text streams; the cue only says captions are up. */
    data object Captions : Cue {
        override val layer get() = Layer.CAPTIONS
    }

    /** A lottie lower third, by its preset name. */
    data class LowerThird(val name: String) : Cue {
        override val layer get() = Layer.GRAPHICS
    }

    /** A scrolling or static announcement, or a countdown. */
    data class Announcement(val text: String) : Cue {
        override val layer get() = Layer.ANNOUNCEMENTS
    }

    /**
     * Operator text, e.g. a nursery call; going live with one clears every other layer -- see
     * [Layer]. [text] is as shown, its tokens already filled in; [template] names the saved message
     * it came from, if any; [durationSeconds] is how long it stays up, or null until cleared.
     */
    data class Message(val text: String, val template: String? = null, val durationSeconds: Int? = null) : Cue {
        override val layer get() = Layer.MESSAGES
    }

    /** Audio-only media; not drawn. */
    data class Audio(val url: String) : Cue {
        override val layer get() = Layer.AUDIO
    }
}
