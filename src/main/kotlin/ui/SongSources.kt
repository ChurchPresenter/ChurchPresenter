package ui

/** Where a source format sits in the "Convert from" rail. */
enum class SourceGroup { SONGS, DOCUMENTS }

/**
 * One entry in the "Convert from" rail.
 *
 * [name] and [ext] are product names and file extensions, so they are deliberately not translated.
 * [description] and [accepts] come from the string bundle, keyed by [id].
 *
 * Only formats the converter can actually read are listed — a format with no converter behind it has
 * no entry point at all.
 */
data class SongSource(
    val id: String,
    val group: SourceGroup,
    val name: String,
    val ext: String,
    val initials: String,
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

    /** Alphabetical, so the rail is scanned by name — the documents group stays last. */
    val all: List<SongSource> = listOf(
        SongSource(EASYSLIDES, SourceGroup.SONGS, "EasySlides", ".xml", "ES"),
        SongSource(FREESHOW, SourceGroup.SONGS, "FreeShow", ".show", "FS"),
        SongSource(FREEWORSHIP, SourceGroup.SONGS, "Free Worship", ".xml", "FW"),
        SongSource(OPENLP, SourceGroup.SONGS, "OpenLP", ".sqlite/.xml", "OL"),
        SongSource(OPENSONG, SourceGroup.SONGS, "OpenSong", ".xml", "OS"),
        SongSource(QUELEA, SourceGroup.SONGS, "Quelea", ".qsp/.xml", "QU"),
        SongSource(SOFTPROJECTOR, SourceGroup.SONGS, "SoftProjector", ".sps", "SP"),
        SongSource(SONGBEAMER, SourceGroup.SONGS, "SongBeamer", ".sng", "SB"),
        SongSource(DOCUMENTS, SourceGroup.DOCUMENTS, "Documents", "pdf/pptx", "DO"),
    )

    /**
     * Named rather than "whatever sorts first": SongBeamer is the format that converts most
     * faithfully, so it stays the panel people land on when the rail is reordered.
     */
    val default: SongSource = all.first { it.id == SONGBEAMER }

    fun byId(id: String): SongSource = all.firstOrNull { it.id == id } ?: default

    /** Case-insensitive match on the product name or its extension. */
    fun matching(query: String): List<SongSource> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { it.name.lowercase().contains(q) || it.ext.lowercase().contains(q) }
    }

    fun groupLabel(group: SourceGroup): String = when (group) {
        SourceGroup.SONGS -> Strings.groupSongFormats
        SourceGroup.DOCUMENTS -> Strings.groupDocuments
    }
}
