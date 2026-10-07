package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.DOCUMENTS_SOURCE
import org.churchpresenter.helper.action.HelperAction

/**
 * "How do I convert songs from OpenLP", "import my SongBeamer songs", "open the converter": the
 * Converter, on its Songs tab, with the program chosen when one is named. A Bible is the Bible
 * rules' to answer.
 */
internal fun convertSongsRule(r: Request): Resolution? {
    if (Vocabulary.BIBLE_NAMES.any { r.text.containsWordPrefix(it) }) return null
    val source = Vocabulary.SONG_SOURCES.firstOrNull { s -> s.phrases.any { r.text.containsPhrase(it) } }
    val aboutSongs = r.has(Vocabulary.SONG) || r.says("library", "song library")
    val converting = r.has(Vocabulary.CONVERT)
    val fromDocuments = aboutSongs && converting && r.has(Vocabulary.SONG_DOCUMENTS)
    return when {
        source != null -> act(HelperAction.OpenConverter(source.id, source.name))
        fromDocuments -> act(HelperAction.OpenConverter(DOCUMENTS_SOURCE))
        r.says("converter") || converting && aboutSongs -> act(HelperAction.OpenConverter())
        else -> null
    }
}
