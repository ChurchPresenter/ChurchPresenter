package org.churchpresenter.converter.ui

/** Where a source format sits in the "Convert from" rail. */
enum class SourceGroup { SONGS, DOCUMENTS }

/**
 * One entry in the "Convert from" rail.
 *
 * [name] and [ext] are product names and file extensions, so they are deliberately not translated.
 * [description] and [accepts] come from the string bundle, keyed by [id].
 *
 * Only formats the converter can actually read are listed — a format with no converter behind it has
 * no entry point at all. [hidden] is the other direction: the converter exists and is tested, but
 * the format is held back from the rail until it has been run against real exported libraries.
 */
data class SongSource(
    val id: String,
    val group: SourceGroup,
    val name: String,
    val ext: String,
    val initials: String,
    val hidden: Boolean = false,
) {
    val description: String get() = Strings.sourceDescription(id)
    val accepts: String get() = Strings.sourceAccepts(id)
}

object SongSources {
    const val SONGBEAMER = "songbeamer"
    const val SOFTPROJECTOR = "softprojector"
    const val DOCUMENTS = "documents"
    const val FREEWORSHIP = "freeworship"
    const val OPENLP = "openlp"
    const val OPENSONG = "opensong"
    const val FREESHOW = "freeshow"
    const val EASYSLIDES = "easyslides"
    const val QUELEA = "quelea"
    const val PROPRESENTER = "propresenter"
    const val EASYWORSHIP = "easyworship"
    const val MEDIASHOUT = "mediashout"
    const val VIDEOPSALM = "videopsalm"

    /** Alphabetical, so the rail is scanned by name — the documents group stays last. */
    val all: List<SongSource> = listOf(
        SongSource(EASYSLIDES, SourceGroup.SONGS, "EasySlides", ".xml", "ES"),
        SongSource(EASYWORSHIP, SourceGroup.SONGS, "EasyWorship", ".db/.ews", "EW", hidden = true),
        SongSource(FREESHOW, SourceGroup.SONGS, "FreeShow", ".show", "FS"),
        SongSource(FREEWORSHIP, SourceGroup.SONGS, "Free Worship", ".xml", "FW"),
        SongSource(MEDIASHOUT, SourceGroup.SONGS, "MediaShout", ".sc7x", "MS", hidden = true),
        SongSource(OPENLP, SourceGroup.SONGS, "OpenLP", ".sqlite/.xml", "OL"),
        SongSource(OPENSONG, SourceGroup.SONGS, "OpenSong", ".xml", "OS"),
        SongSource(PROPRESENTER, SourceGroup.SONGS, "ProPresenter", ".pro/.pro6", "PP", hidden = true),
        SongSource(QUELEA, SourceGroup.SONGS, "Quelea", ".qsp/.xml", "QU"),
        SongSource(SOFTPROJECTOR, SourceGroup.SONGS, "SoftProjector", ".sps", "SP"),
        SongSource(SONGBEAMER, SourceGroup.SONGS, "SongBeamer", ".sng", "SB"),
        SongSource(VIDEOPSALM, SourceGroup.SONGS, "VideoPsalm", ".json", "VP"),
        SongSource(DOCUMENTS, SourceGroup.DOCUMENTS, "Documents", "pdf/pptx", "DO"),
    )

    /**
     * What the rail shows. [all] stays whole so the converters behind the hidden entries keep their
     * tests and the two lists can still be checked against each other; only the way in is withheld.
     */
    val visible: List<SongSource> = all.filterNot { it.hidden }

    /**
     * Named rather than "whatever sorts first": SongBeamer is the format that converts most
     * faithfully, so it stays the panel people land on when the rail is reordered.
     */
    val default: SongSource = all.first { it.id == SONGBEAMER }

    fun byId(id: String): SongSource = all.firstOrNull { it.id == id } ?: default

    /** Case-insensitive match on the product name or its extension. */
    fun matching(query: String): List<SongSource> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return visible
        return visible.filter { it.name.lowercase().contains(q) || it.ext.lowercase().contains(q) }
    }

    fun groupLabel(group: SourceGroup): String = when (group) {
        SourceGroup.SONGS -> Strings.groupSongFormats
        SourceGroup.DOCUMENTS -> Strings.groupDocuments
    }
}
