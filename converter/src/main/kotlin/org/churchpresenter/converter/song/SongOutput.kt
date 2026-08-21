package org.churchpresenter.converter.song

import java.io.File

/**
 * Names and writes the `.song` files a library import produces.
 *
 * Every format that keeps a whole library in one input — an EasySlides export, a Quelea song pack,
 * an OpenLP database — needs the same three things from this, and each of them loses songs silently
 * when it is wrong: a name every filesystem accepts, the library's own numbering kept as a prefix so
 * its order survives the import, and a collision guard, because two songs sharing a title is normal
 * in a hymnal and the second would otherwise overwrite the first.
 */
internal object SongOutput {

    private val illegalCharacters = Regex("""[/\\:*?"<>|]""")
    private val controlCharacters = Regex("""\p{Cntrl}""")
    private val runsOfWhitespace = Regex("""\s+""")
    private const val NUMBER_WIDTH = 4

    fun sanitizeName(name: String): String = name
        .replace(illegalCharacters, " ")
        .replace(controlCharacters, "")
        .replace(runsOfWhitespace, " ")
        .trim()
        .trimEnd('.')

    /** `0012 - Amazing Grace.song`, or `Amazing Grace.song` where the library does not number. */
    fun fileName(title: String, number: String = "", fallback: String = "Song"): String {
        val safeTitle = sanitizeName(title).ifBlank { fallback }
        val digits = number.trim()
        val prefix = if (digits.isEmpty()) "" else "${digits.padStart(NUMBER_WIDTH, '0')} - "
        return "$prefix$safeTitle.song"
    }

    /** [name] with ` (2)`, ` (3)` … appended until nothing in [taken] already claims it. */
    fun uniqueFile(directory: File, name: String, taken: MutableSet<String>): File {
        val base = name.removeSuffix(".song")
        var candidate = name
        var attempt = 1
        while (!taken.add(candidate.lowercase())) {
            attempt++
            candidate = "$base ($attempt).song"
        }
        return File(directory, candidate)
    }

    /** Writes one song into [directory], returning the file it landed in. */
    fun write(directory: File, song: ParsedSong, taken: MutableSet<String>, number: String = ""): File {
        directory.mkdirs()
        val file = uniqueFile(directory, fileName(song.title, number), taken)
        file.writeText(MarkdownToSongConverter.buildSongContent(song), Charsets.UTF_8)
        return file
    }
}
