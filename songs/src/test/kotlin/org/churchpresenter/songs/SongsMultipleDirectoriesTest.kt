package org.churchpresenter.songs

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ResourceDirectory
import org.churchpresenter.settings.SongSettings
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SongsMultipleDirectoriesTest {
    @Test
    fun `songs from primary and additional roots are merged`() {
        val root = Files.createTempDirectory("song-multiple-roots").toFile()
        val first = root.resolve("first").apply { mkdirs() }
        val second = root.resolve("second").apply { mkdirs() }
        val parser = SongFileParser()
        parser.writeSongFile(SongItem("1", "First hymn"), first.resolve("first.song").absolutePath)
        parser.writeSongFile(SongItem("2", "Second hymn"), second.resolve("second.song").absolutePath)
        val model = SongsViewModel(
            AppSettings(songSettings = SongSettings(storageDirectory = first.absolutePath),
                additionalResourceDirectories = mapOf(ResourceDirectory.SONGS to listOf(second.absolutePath))),
            dispatcher = Dispatchers.Unconfined, ioDispatcher = Dispatchers.Unconfined,
            enableFolderWatcher = false,
        )
        try {
            assertEquals(setOf("First hymn", "Second hymn"), model.songsData.value.getSongs().map { it.title }.toSet())
        } finally {
            model.dispose()
            root.deleteRecursively()
        }
    }
}
