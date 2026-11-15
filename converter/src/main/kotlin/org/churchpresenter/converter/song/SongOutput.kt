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
 *
 * The app reads a song's number from digits at the very start of its file name and nowhere else, so
 * only digits ever go there — see [numberOf] for what becomes of a number a library prints otherwise.
 */
internal object SongOutput {

    private val illegalCharacters = Regex("""[/\\:*?"<>|]""")
    private val controlCharacters = Regex("""\p{Cntrl}""")
    private val runsOfWhitespace = Regex("""\s+""")
    private const val NUMBER_WIDTH = 4

    /** Digits with no other digit before them: the number in `WP Scores 012` or `SOP-12`, not in `12/3`. */
    private val trailingNumber = Regex("""^\D*?(\d+)$""")

    fun sanitizeName(name: String): String = name
        .replace(illegalCharacters, " ")
        .replace(controlCharacters, "")
        .replace(runsOfWhitespace, " ")
        .trim()
        .trimEnd('.')

    /**
     * A library's song number as the app can use it.
     *
     * `104` is the number. `WP Scores 012` is a book's name in front of its number — the folder
     * already names the book — so it is 12. Anything else (`12a`, `12/3`, `Intro`) is no number the
     * app can sort on, and is kept whole as the song's alias, which the app shows in its place.
     */
    fun numberOf(printed: String): SongNumber {
        val text = printed.trim()
        val digits = trailingNumber.find(text)?.groupValues?.get(1)
        return when {
            text.isEmpty() -> SongNumber()
            digits != null -> SongNumber(digits = digits)
            else -> SongNumber(alias = text)
        }
    }

    /** `0012 - Amazing Grace.song`, or `Amazing Grace.song` where the library does not number. */
    fun fileName(title: String, number: String = "", fallback: String = "Song"): String {
        val safeTitle = sanitizeName(title).ifBlank { fallback }
        val digits = number.trim().takeIf { it.all(Char::isDigit) }.orEmpty()
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

    /**
     * Writes one song into [directory], returning the file it landed in.
     *
     * [number] is what the library prints; when it holds no number ([numberOf]), [position] — the
     * song's place in the library — numbers the file instead, and the printed text becomes the alias.
     */
    fun write(
        directory: File,
        song: ParsedSong,
        taken: MutableSet<String>,
        number: String = "",
        position: String = "",
    ): File {
        directory.mkdirs()
        val printed = numberOf(number)
        val digits = printed.digits.ifEmpty { position }
        val named = if (song.alias.isBlank() && printed.alias.isNotEmpty()) song.copy(alias = printed.alias) else song
        val file = uniqueFile(directory, fileName(named.title, digits), taken)
        file.writeText(MarkdownToSongConverter.buildSongContent(named), Charsets.UTF_8)
        return file
    }
}

/** A library's song number split for the app: [digits] for the file name, or [alias] to show instead. */
internal data class SongNumber(val digits: String = "", val alias: String = "")
