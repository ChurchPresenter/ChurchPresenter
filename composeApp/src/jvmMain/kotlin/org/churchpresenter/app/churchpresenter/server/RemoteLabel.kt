package org.churchpresenter.app.churchpresenter.server

/**
 * A piece of a remote-activity toast, as the server knows it.
 *
 * The routes run off the UI thread and have no string resources, so they say *what* happened and
 * leave the wording to the desktop, which formats each label in the operator's language. A name the
 * remote sent or the server read from disk (a file, a verse reference, a folder) is shown as it is.
 */
sealed interface RemoteLabel {
    /** Shown exactly as given. */
    data class Text(val value: String) : RemoteLabel

    /** "Song 42" — the number as the song book writes it, which need not be an integer. */
    data class Song(val number: String) : RemoteLabel

    /** "Section 2", for the section index the remote asked for. */
    data class Section(val index: Int) : RemoteLabel

    /** "Slide 3", one-based. */
    data class Slide(val number: Int) : RemoteLabel

    /** "Image 1", for a picture sent by index with no file name. */
    data class Image(val index: Int) : RemoteLabel

    /** An upload's size, worded as whole kilobytes ("512 KB"). */
    data class Size(val bytes: Long) : RemoteLabel

    companion object {
        val EMPTY: RemoteLabel = Text("")
    }
}
