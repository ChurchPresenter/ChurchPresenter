package org.churchpresenter.bibleedits

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.churchpresenter.diagnostics.Log
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The history of every change, kept in one file of its own beside the app's settings. */
class BibleEditsStore(folder: File) {

    private val file = File(folder, FILE_NAME)

    fun load(): List<EditEntry> {
        if (!file.isFile) return emptyList()
        return try {
            json.decodeFromString(BibleEditsFile.serializer(), file.readText()).entries
        } catch (e: IOException) {
            Log.warn(TAG, "Bible edits could not be read from ${file.path}: ${e.message}")
            emptyList()
        } catch (e: SerializationException) {
            Log.warn(TAG, "Bible edits in ${file.path} are not readable: ${e.message}")
            emptyList()
        }
    }

    /** Writes through a temporary file, so a crash mid-write leaves the last good history. */
    fun save(entries: List<EditEntry>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(json.encodeToString(BibleEditsFile.serializer(), BibleEditsFile(entries = entries)))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        const val FILE_NAME = "bible-edits.json"
        const val TAG = "BibleEditor"
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }
}
