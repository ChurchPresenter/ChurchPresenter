package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.flow.MutableStateFlow
import org.churchpresenter.settings.utils.Constants
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The pictures the companion API can serve: the folder currently open in the Pictures tab, every
 * picture folder sitting in the schedule, and anything a device has uploaded this session.
 *
 * Owns its own state, which is why it is a class rather than another cluster of `_`-prefixed
 * fields on `CompanionServer`. Broadcasting is deliberately NOT here — [update] returns the new
 * catalogue and the server decides what to tell clients, so this stays testable without a server.
 */
internal class PictureLibrary {

    companion object {
        /** Stable folder ID used for all device-uploaded photos (accumulates across sessions). */
        const val DEVICE_UPLOADS_FOLDER_ID = "device_uploads"

        /** Recognised image extensions — matches PicturesViewModel. */
        private val IMAGE_EXTENSIONS =
            setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
    }

    /** The folder currently open in the Pictures tab; null until one has been loaded. */
    val catalog = MutableStateFlow<PictureFolderResponse?>(null)

    /** folderId → ordered list of image Files (index = image order). */
    val files = ConcurrentHashMap<String, List<File>>()

    /** folderId → catalog metadata (covers the active folder and all schedule picture items). */
    val catalogs = ConcurrentHashMap<String, PictureFolderResponse>()

    /** The folder-id served by GET /api/pictures. Null until a folder has been loaded. */
    val activeFolderId: String? get() = catalog.value?.folderId

    /**
     * Publishes [imageFiles] as the active folder and returns the catalogue built for it. File
     * references are stored rather than bytes — images are read on demand when a client asks.
     */
    fun update(
        folderId: String,
        folderName: String,
        folderPath: String,
        imageFiles: List<File>,
    ): PictureFolderResponse {
        files[folderId] = imageFiles.toList()
        val built = catalogFor(folderId, folderName, folderPath, imageFiles)
        catalog.value = built
        catalogs[folderId] = built
        return built
    }

    /** The [File] for one image, or null when the folder or index is unknown. */
    fun imageFile(folderId: String, index: Int): File? = files[folderId]?.getOrNull(index)

    /**
     * Scans [folderPath] and caches it under [id] (a schedule item's UUID) so a phone can browse a
     * scheduled folder that was never opened in the Pictures tab. No-op if [id] is already known.
     * Must be called on an IO thread.
     */
    fun registerScheduleFolder(id: String, folderPath: String, folderName: String) {
        if (files.containsKey(id)) return
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) return
        val imageFiles = folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
            ?.sortedBy { it.name }
            ?: return
        if (imageFiles.isEmpty()) return
        files[id] = imageFiles
        catalogs[id] = catalogFor(id, folderName, folderPath, imageFiles)
    }

    /**
     * Empties the device_uploads tree so device photos are session-only, and drops every
     * in-memory entry whose folder-id belongs to it (any date, plus legacy flat entries).
     */
    fun clearDeviceUploads() {
        File(System.getProperty("user.home"), ".churchpresenter/device_uploads").deleteRecursively()
        files.keys
            .filter { it == DEVICE_UPLOADS_FOLDER_ID || it.startsWith("${DEVICE_UPLOADS_FOLDER_ID}_") }
            .forEach { id -> files.remove(id); catalogs.remove(id) }
    }

    /** Which registered folder (if any) holds [path], as folderId to index. */
    fun locate(path: String?): Pair<String?, Int?> {
        if (path.isNullOrEmpty()) return null to null
        for ((folderId, folderFiles) in files) {
            val idx = folderFiles.indexOfFirst { it.absolutePath == path }
            if (idx >= 0) return folderId to idx
        }
        return null to null
    }

    private fun catalogFor(
        folderId: String,
        folderName: String,
        folderPath: String,
        imageFiles: List<File>,
    ) = PictureFolderResponse(
        folderId = folderId,
        folderName = folderName,
        folderPath = folderPath,
        imageTotal = imageFiles.size,
        images = imageFiles.mapIndexed { index, file ->
            PictureFileDto(
                index = index,
                fileName = file.name,
                thumbnailUrl = "${Constants.ENDPOINT_PICTURES}/$folderId/images/$index"
            )
        }
    )
}
