package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.charset.StandardCharsets

data class ConversionResult(
    val songsConverted: Int,
    val songbookFolder: String,
    val errors: List<String>
)

class SpsConverter {

    fun convertSpsToSongFiles(spsFilePath: String, outputDirectory: String): ConversionResult {
        val errors = mutableListOf<String>()
        var songsConverted = 0

        try {
            // Load songs from the SPS file
            val songs = Songs()
            songs.loadFromSps(spsFilePath)
            val songList = songs.getSongs()

            if (songList.isEmpty()) {
                return ConversionResult(0, "", listOf("No songs found in file"))
            }

            // Use the songbook name from the first song, or the filename
            val songbookName = songList.first().songbook.ifEmpty {
                File(spsFilePath).nameWithoutExtension
            }

            // Create songbook folder
            val songbookDir = File(outputDirectory, sanitizeName(songbookName))
            if (!songbookDir.exists()) {
                songbookDir.mkdirs()
            }

            val parser = SongFileParser()

            for (song in songList) {
                try {
                    val paddedNumber = song.number.padStart(4, '0')
                    val sanitizedTitle = sanitizeName(song.title)
                    val fileName = "$paddedNumber - $sanitizedTitle.song"
                    val filePath = File(songbookDir, fileName).absolutePath

                    parser.writeSongFile(song, filePath)
                    songsConverted++
                } catch (e: Exception) {
                    errors.add("Error converting song ${song.number} - ${song.title}: ${e.message}")
                }
            }

            return ConversionResult(songsConverted, songbookDir.absolutePath, errors)
        } catch (e: Exception) {
            return ConversionResult(songsConverted, "", listOf("Error reading SPS file: ${e.message}"))
        }
    }

    fun getTargetFolderName(spsFilePath: String, outputDirectory: String): String? {
        try {
            val songs = Songs()
            songs.loadFromSps(spsFilePath)
            val songList = songs.getSongs()
            if (songList.isEmpty()) return null
            val songbookName = songList.first().songbook.ifEmpty {
                File(spsFilePath).nameWithoutExtension
            }
            return sanitizeName(songbookName)
        } catch (_: Exception) {
            return null
        }
    }

    fun targetFolderExists(spsFilePath: String, outputDirectory: String): Boolean {
        val folderName = getTargetFolderName(spsFilePath, outputDirectory) ?: return false
        return File(outputDirectory, folderName).exists()
    }

    companion object {
        private val ILLEGAL_CHARS = Regex("""[/\\:*?"<>|]""")
        private val CONTROL_CHARS = Regex("""[\x00-\x1F\x7F]""")
        private val NON_PRINTABLE = Regex("""[^\p{Print}\p{L}\p{M}\p{N}\p{P}\p{Z}]""")
        private val WHITESPACE_RUN = Regex("""\s+""")
    }

    private fun sanitizeName(name: String): String {
        return name
            .replace(ILLEGAL_CHARS, " ")
            .replace(CONTROL_CHARS, "")
            .replace(NON_PRINTABLE, " ")
            .replace(WHITESPACE_RUN, " ")
            .trim()
    }
}
