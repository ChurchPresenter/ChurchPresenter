package converter.song

import java.io.File

/** What one conversion produced: the files written, plus anything that went wrong along the way. */
data class SongConversionResult(
    val outputFiles: List<File>,
    val errors: List<String> = emptyList()
)

/**
 * What an input file turns out to contain. Deliberately structured rather than pre-formatted: the
 * converter layer has no access to the string bundle, so the UI does the wording.
 */
data class SongPreviewInfo(
    val title: String = "",
    val sectionCount: Int = 0,
    val songCount: Int = 0,
    val verseOrder: List<String> = emptyList()
)

/**
 * One song format the converter can read.
 *
 * Adding a format means adding an implementation here and an entry in the UI's source rail —
 * nothing else in the app needs to know the format exists.
 */
interface SongFormatConverter {
    /** Stable identifier, shared with the rail entry that selects this format. */
    val id: String

    /** Extensions offered by the file picker, without the leading dot. */
    val extensions: List<String>

    /** True when one input expands into many files, so it needs an output folder of its own. */
    val needsOutputFolder: Boolean

    /** False for formats where a single input is the whole library. */
    val allowsMultipleFiles: Boolean get() = true

    /**
     * True where the format's files usually carry no extension at all.
     *
     * OpenSong writes its songs with a bare name, so an extension filter hides every one of them in
     * the picker and a folder scan finds nothing — the panel then looks like it works and converts
     * an empty selection.
     */
    val acceptsExtensionlessFiles: Boolean get() = false

    /** Converts one input, writing beside it when [outputDir] is null. */
    fun convert(input: File, outputDir: File?): SongConversionResult

    /** What the input holds, for the preview list. */
    fun describe(input: File): SongPreviewInfo

    /** What this input is written out as. */
    fun outputNameFor(input: File): String
}

object SongFormatConverters {
    val all: List<SongFormatConverter> =
        listOf(
            EasySlidesFormat,
            FreeShowFormat,
            FreeWorshipFormat,
            OpenLpFormat,
            OpenSongFormat,
            QueleaFormat,
            SoftProjectorFormat,
            SongBeamerFormat,
            DocumentFormat,
        )

    fun byId(id: String): SongFormatConverter =
        all.firstOrNull { it.id == id } ?: SongBeamerFormat
}

/** SongBeamer `.sng` text files — one song per file. */
object SongBeamerFormat : SongFormatConverter {
    override val id = "songbeamer"
    override val extensions = listOf("sng")
    override val needsOutputFolder = false

    override fun convert(input: File, outputDir: File?): SongConversionResult {
        val outFile = File(outputDir ?: input.parentFile, outputNameFor(input))
        SngToSongConverter.convert(input, outFile)
        return SongConversionResult(listOf(outFile))
    }

    override fun describe(input: File): SongPreviewInfo {
        val song = SngToSongConverter.parse(input)
        return SongPreviewInfo(song.title, sectionCount = song.sections.size, verseOrder = song.verseOrder)
    }

    override fun outputNameFor(input: File) = input.nameWithoutExtension + ".song"
}

/** Free Worship exports — OpenLyrics XML, one song per file. */
object FreeWorshipFormat : SongFormatConverter {
    override val id = "freeworship"
    override val extensions = listOf("xml")
    override val needsOutputFolder = false

    override fun convert(input: File, outputDir: File?): SongConversionResult {
        val outFile = File(outputDir ?: input.parentFile, outputNameFor(input))
        FreeWorshipConverter.convert(input, outFile)
        return SongConversionResult(listOf(outFile))
    }

    override fun describe(input: File): SongPreviewInfo {
        val song = FreeWorshipConverter.parse(input)
        return SongPreviewInfo(song.title, sectionCount = song.sections.size, verseOrder = song.verseOrder)
    }

    override fun outputNameFor(input: File) = FreeWorshipConverter.outputNameFor(input)
}

/** OpenLP libraries: either the `songs.sqlite` database itself or an OpenLyrics XML export. */
object OpenLpFormat : SongFormatConverter {
    override val id = "openlp"
    override val extensions = listOf("xml", "sqlite", "db")
    override val needsOutputFolder = true

    override fun convert(input: File, outputDir: File?): SongConversionResult {
        requireNotNull(outputDir) { "OpenLP libraries need an output folder" }
        if (OpenLpDatabaseConverter.isDatabase(input)) return OpenLpDatabaseConverter.convert(input, outputDir)
        val outFile = File(outputDir, outputNameFor(input))
        FreeWorshipConverter.convert(input, outFile)
        return SongConversionResult(listOf(outFile))
    }

    override fun describe(input: File): SongPreviewInfo {
        if (OpenLpDatabaseConverter.isDatabase(input)) {
            return SongPreviewInfo(input.nameWithoutExtension, songCount = OpenLpDatabaseConverter.parse(input).size)
        }
        val song = FreeWorshipConverter.parse(input)
        return SongPreviewInfo(song.title, sectionCount = song.sections.size, verseOrder = song.verseOrder)
    }

    override fun outputNameFor(input: File): String =
        if (OpenLpDatabaseConverter.isDatabase(input)) input.nameWithoutExtension
        else input.nameWithoutExtension + ".song"
}

/** OpenSong song files — XML metadata around a plain-text lyrics body, one song per file. */
object OpenSongFormat : SongFormatConverter {
    override val id = "opensong"
    override val extensions = listOf("xml")
    override val needsOutputFolder = false
    override val acceptsExtensionlessFiles = true

    override fun convert(input: File, outputDir: File?): SongConversionResult {
        val outFile = File(outputDir ?: input.parentFile, outputNameFor(input))
        OpenSongConverter.convert(input, outFile)
        return SongConversionResult(listOf(outFile))
    }

    override fun describe(input: File): SongPreviewInfo {
        val song = OpenSongConverter.parse(input)
        return SongPreviewInfo(
            song.title.ifBlank { input.nameWithoutExtension },
            sectionCount = song.sections.size,
            verseOrder = song.verseOrder,
        )
    }

    override fun outputNameFor(input: File) = input.nameWithoutExtension + ".song"
}

/** FreeShow `.show` files — JSON, one show per file. */
object FreeShowFormat : SongFormatConverter {
    override val id = "freeshow"
    override val extensions = listOf("show", "json")
    override val needsOutputFolder = false

    override fun convert(input: File, outputDir: File?): SongConversionResult {
        val outFile = File(outputDir ?: input.parentFile, outputNameFor(input))
        FreeShowConverter.convert(input, outFile)
        return SongConversionResult(listOf(outFile))
    }

    override fun describe(input: File): SongPreviewInfo {
        val song = FreeShowConverter.parse(input)
        return SongPreviewInfo(song.title.ifBlank { input.nameWithoutExtension }, sectionCount = song.sections.size)
    }

    override fun outputNameFor(input: File) = input.nameWithoutExtension + ".song"
}

/** EasySlides XML exports — one file is a whole library of `<Item>` songs. */
object EasySlidesFormat : SongFormatConverter {
    override val id = "easyslides"
    override val extensions = listOf("xml")
    override val needsOutputFolder = true

    override fun convert(input: File, outputDir: File?): SongConversionResult {
        requireNotNull(outputDir) { "EasySlides exports need an output folder" }
        return EasySlidesConverter.convert(input, outputDir)
    }

    override fun describe(input: File): SongPreviewInfo {
        val songs = EasySlidesConverter.parse(input)
        return SongPreviewInfo(
            songs.firstOrNull()?.title.orEmpty(),
            sectionCount = songs.firstOrNull()?.sections?.size ?: 0,
            songCount = songs.size,
            verseOrder = songs.firstOrNull()?.sequence.orEmpty(),
        )
    }

    override fun outputNameFor(input: File) = input.nameWithoutExtension
}

/** Quelea song packs, and the loose song XML files they hold. */
object QueleaFormat : SongFormatConverter {
    override val id = "quelea"
    override val extensions = listOf("qsp", "xml")
    override val needsOutputFolder = true

    override fun convert(input: File, outputDir: File?): SongConversionResult {
        requireNotNull(outputDir) { "Quelea song packs need an output folder" }
        return QueleaConverter.convert(input, outputDir)
    }

    override fun describe(input: File): SongPreviewInfo {
        val songs = QueleaConverter.parse(input)
        return SongPreviewInfo(
            songs.firstOrNull()?.title.orEmpty(),
            sectionCount = songs.firstOrNull()?.sections?.size ?: 0,
            songCount = songs.size,
            verseOrder = songs.firstOrNull()?.sequence.orEmpty(),
        )
    }

    override fun outputNameFor(input: File) = input.nameWithoutExtension
}

/** SoftProjector `.sps` song books — one input becomes a folder of songs. */
object SoftProjectorFormat : SongFormatConverter {
    override val id = "softprojector"
    override val extensions = listOf("sps")
    override val needsOutputFolder = true

    override fun convert(input: File, outputDir: File?): SongConversionResult {
        requireNotNull(outputDir) { "SoftProjector song books need an output folder" }
        val result = SpsToSongConverter.convert(input, outputDir)
        val folder = File(result.songbookFolder)
        val written = folder.listFiles { f -> f.extension.equals("song", ignoreCase = true) }?.toList().orEmpty()
        return SongConversionResult(written, result.errors)
    }

    override fun describe(input: File): SongPreviewInfo {
        val result = SpsToSongConverter.parse(input)
        return SongPreviewInfo(result.songbookName, songCount = result.songs.size)
    }

    override fun outputNameFor(input: File) = SpsToSongConverter.getTargetFolderName(input)
}

/** PDF, PowerPoint and Word documents, split into songs by their headings. */
object DocumentFormat : SongFormatConverter {
    override val id = "documents"
    override val extensions = listOf("pdf", "docx", "pptx")
    override val needsOutputFolder = true

    override fun convert(input: File, outputDir: File?): SongConversionResult {
        requireNotNull(outputDir) { "Documents need an output folder" }
        val extracted = DocumentTextExtractor.extract(input)
        if (!extracted.success) {
            return SongConversionResult(emptyList(), listOfNotNull(extracted.errorMessage))
        }
        val result = MarkdownToSongConverter.convert(extracted.text, input.name, outputDir)
        return SongConversionResult(result.outputFiles, result.errors)
    }

    override fun describe(input: File): SongPreviewInfo {
        val (_, songs) = MarkdownToSongConverter.preview(input)
        return SongPreviewInfo(songCount = songs.size)
    }

    override fun outputNameFor(input: File) = input.nameWithoutExtension
}
