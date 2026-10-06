package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.songs.Songs
import org.churchpresenter.core.models.songs.SongFileParser
import java.io.File
import java.io.IOException
import java.sql.SQLException

data class ConversionResult(
    val songsConverted: Int,
    val songbookFolder: String,
    val errors: List<ConversionError>
)

sealed interface ConversionError {
    data object NoSongs : ConversionError
    data class ReadFailed(val reason: String?) : ConversionError
    data class SongFailed(val number: String, val title: String, val reason: String?) : ConversionError
}

class SpsConverter {

    fun convertSpsToSongFiles(spsFilePath: String, outputDirectory: String): ConversionResult {
        val errors = mutableListOf<ConversionError>()
        var songsConverted = 0

        return try {
            // Load songs from the SPS file
            val songs = Songs()
            songs.loadFromSps(spsFilePath)
            val songList = songs.getSongs()

            if (songList.isEmpty()) {
                return ConversionResult(0, "", listOf(ConversionError.NoSongs))
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
                } catch (e: IOException) {
                    errors.add(ConversionError.SongFailed(song.number, song.title, e.message))
                }
            }

            ConversionResult(songsConverted, songbookDir.absolutePath, errors)
        } catch (e: IOException) {
            readFailed(songsConverted, e)
        } catch (e: IllegalArgumentException) {
            // A path that is not there.
            readFailed(songsConverted, e)
        } catch (e: SQLException) {
            // A SongPresenter SQLite database that will not open.
            readFailed(songsConverted, e)
        }
    }

    private fun readFailed(songsConverted: Int, e: Exception) =
        ConversionResult(songsConverted, "", listOf(ConversionError.ReadFailed(e.message)))

    fun getTargetFolderName(spsFilePath: String): String? {
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
        val folderName = getTargetFolderName(spsFilePath) ?: return false
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
