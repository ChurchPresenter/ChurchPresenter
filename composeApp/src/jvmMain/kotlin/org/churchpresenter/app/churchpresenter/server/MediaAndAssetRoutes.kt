package org.churchpresenter.app.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.HeicDecoder

/**
 * Routes serving pictures, uploaded media and background assets.
 *
 * Body moved verbatim from `CompanionServer` — raw-string literals make the indentation
 * load-bearing. Picture caches arrive as identically-named parameters; the Bible file paths are
 * mutable and reached through [server].
 */
internal fun Route.mediaAndAssetRoutes(
    server: CompanionServer,
    DEVICE_UPLOADS_FOLDER_ID: String,
    _backgroundSettings: MutableStateFlow<BackgroundSettings>,
    _fileUploadEnabled: MutableStateFlow<Boolean>,
    _pictureCatalog: MutableStateFlow<PictureFolderResponse?>,
    _pictureCatalogs: ConcurrentHashMap<String, PictureFolderResponse>,
    _pictureFiles: ConcurrentHashMap<String, List<File>>,
    _scheduleItemToMediaPath: ConcurrentHashMap<String, String>,
    json: Json,
    scope: CoroutineScope,
) {
                get(Constants.ENDPOINT_PICTURES) {
                    if (!server.checkApiKey(call)) return@get
                    val catalog = _pictureCatalog.value
                    if (catalog == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, "No picture folder loaded")
                        return@get
                    }
                    call.respond(catalog)
                }

                /**
                 * GET /api/pictures/{id}
                 * Returns catalog metadata for the picture folder with {id}.
                 * Works for any schedule picture item (by its schedule UUID) as well as the
                 * currently active folder loaded via the Pictures tab.
                 */
                get("${Constants.ENDPOINT_PICTURES}/{id}") {
                    if (!server.checkApiKey(call)) return@get
                    val id = call.parameters["id"] ?: run { call.respond(HttpStatusCode.BadRequest, "Missing id"); return@get }
                    val catalog = _pictureCatalogs[id]
                    if (catalog == null) {
                        server.logRest("/api/pictures/{id}", 404, "folder_not_found")
                        call.respond(HttpStatusCode.NotFound, "Picture folder not found")
                        return@get
                    }
                    server.logRest("/api/pictures/{id}", 200)
                    call.respond(catalog)
                }

                /**
                 * GET /api/pictures/{id}/images/{index}
                 * Returns the image at {index} as a JPEG for the folder with {id}.
                 */
                get("${Constants.ENDPOINT_PICTURES}/{id}/images/{index}") {
                    if (!server.checkApiKey(call)) return@get
                    val id    = call.parameters["id"]    ?: run { call.respond(HttpStatusCode.BadRequest, "Missing id"); return@get }
                    val index = call.parameters["index"]?.toIntOrNull() ?: run { call.respond(HttpStatusCode.BadRequest, "Invalid index"); return@get }
                    val files = _pictureFiles[id]
                    if (files == null) {
                        server.logRest("/api/pictures/{id}/images/{index}", 404, "folder_not_found")
                        call.respond(HttpStatusCode.NotFound, "Picture folder not found")
                        return@get
                    }
                    if (index < 0 || index >= files.size) {
                        server.logRest("/api/pictures/{id}/images/{index}", 404, "index_out_of_range")
                        call.respond(HttpStatusCode.NotFound, "Image index out of range")
                        return@get
                    }
                    val file = files[index]
                    if (!file.exists()) {
                        server.logRest("/api/pictures/{id}/images/{index}", 404, "file_not_found_on_disk")
                        call.respond(HttpStatusCode.NotFound, "Image file not found on disk")
                        return@get
                    }
                    // HEIC/HEIF are not displayable by browsers — convert to JPEG first
                    val ext = file.extension.lowercase()
                    if (ext == "heic" || ext == "heif") {
                        val jpegBytes = HeicDecoder.toJpegBytes(file)
                        if (jpegBytes != null) {
                            server.logRest("/api/pictures/{id}/images/{index}", 200)
                            call.respondBytes(jpegBytes, ContentType.Image.JPEG)
                        } else {
                            server.logRest("/api/pictures/{id}/images/{index}", 500, "heic_conversion_failed")
                            call.respond(HttpStatusCode.InternalServerError, "Failed to convert HEIC image")
                        }
                    } else {
                        server.logRest("/api/pictures/{id}/images/{index}", 200)
                        call.respondBytes(file.readBytes(), contentTypeForExtension(ext))
                    }
                }

                /**
                 * GET /api/media/stream/{id} — streams a local media file's raw bytes for a schedule
                 * item registered via [server.updateSchedule] (mediaType == "local"). Range requests are
                 * handled by the PartialContent plugin so seeking works, letting an InstanceLink
                 * follower play the file over HTTP without needing a local copy.
                 */
                get("${Constants.ENDPOINT_MEDIA_STREAM}/{id}") {
                    if (!server.checkApiKey(call)) return@get
                    val id = call.parameters["id"] ?: run { call.respond(HttpStatusCode.BadRequest, "Missing id"); return@get }
                    val path = _scheduleItemToMediaPath[id]
                    if (path == null) {
                        server.logRest("/api/media/stream/{id}", 404, "media_item_not_found")
                        call.respond(HttpStatusCode.NotFound, "Media item not found")
                        return@get
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        server.logRest("/api/media/stream/{id}", 404, "file_not_found_on_disk")
                        call.respond(HttpStatusCode.NotFound, "Media file not found on disk")
                        return@get
                    }
                    server.logRest("/api/media/stream/{id}", 200)
                    call.respondFile(file)
                }

                /**
                 * GET /api/bible/file — streams the primary bible's raw .spb file bytes so an
                 * InstanceLink follower can cache and load it through the same Bible.loadFromSpb()
                 * engine used locally (search/cross-reference/numbering all work unchanged), instead
                 * of reimplementing that engine against the API. Range requests are handled by the
                 * PartialContent plugin. The mobile companion API only ever exposed the primary bible;
                 * GET /api/bible/file/secondary below extends that scope for InstanceLink only.
                 */
                get(Constants.ENDPOINT_BIBLE_FILE) {
                    if (!server.checkApiKey(call)) return@get
                    val path = server._bibleFilePath
                    if (path.isEmpty()) {
                        server.logRest("/api/bible/file", 404, "no_bible_loaded")
                        call.respond(HttpStatusCode.NotFound, "No bible loaded")
                        return@get
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        server.logRest("/api/bible/file", 404, "file_not_found_on_disk")
                        call.respond(HttpStatusCode.NotFound, "Bible file not found on disk")
                        return@get
                    }
                    server.logRest("/api/bible/file", 200)
                    call.respondFile(file)
                }

                /** GET /api/bible/file/secondary — same as above, for a follower that opted in to
                 *  mirroring the primary's secondary bible instead of keeping its own. */
                get("${Constants.ENDPOINT_BIBLE_FILE}/secondary") {
                    if (!server.checkApiKey(call)) return@get
                    val path = server._secondaryBibleFilePath
                    if (path.isEmpty()) {
                        server.logRest("/api/bible/file/secondary", 404, "no_secondary_bible_loaded")
                        call.respond(HttpStatusCode.NotFound, "No secondary bible loaded")
                        return@get
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        server.logRest("/api/bible/file/secondary", 404, "file_not_found_on_disk")
                        call.respond(HttpStatusCode.NotFound, "Secondary bible file not found on disk")
                        return@get
                    }
                    server.logRest("/api/bible/file/secondary", 200)
                    call.respondFile(file)
                }

                /** Ordered Bible module names available to an Instance Link follower. */
                get("${Constants.ENDPOINT_BIBLE_FILE}/translations") {
                    if (!server.checkApiKey(call)) return@get
                    call.respond(server._bibleFilePaths.map { File(it).name })
                }

                /** Downloads one Bible module by its stable position in the ordered manifest. */
                get("${Constants.ENDPOINT_BIBLE_FILE}/translation/{index}") {
                    if (!server.checkApiKey(call)) return@get
                    val index = call.parameters["index"]?.toIntOrNull()
                    val path = index?.let { server._bibleFilePaths.getOrNull(it) }
                    if (path == null || !File(path).exists()) {
                        call.respond(HttpStatusCode.NotFound, "Bible translation not found")
                        return@get
                    }
                    call.respondFile(File(path))
                }

                /** GET /api/backgrounds — current BackgroundSettings as JSON. Image/video fields are
                 *  still local file paths on this machine; a follower resolves the actual bytes via
                 *  GET /api/backgrounds/asset/{slot} below, keyed by slot rather than raw path. */
                get(Constants.ENDPOINT_BACKGROUNDS) {
                    if (!server.checkApiKey(call)) return@get
                    server.logRest("/api/backgrounds", 200)
                    call.respond(_backgroundSettings.value)
                }

                /**
                 * GET /api/backgrounds/asset/{slot}?type=image|video — streams the background
                 * image/video file currently configured for one slot (default, defaultLowerThird,
                 * bible, bibleLowerThird, song, songLowerThird). Keyed by slot name rather than a raw
                 * path, same reasoning as the lower-third-by-name endpoint above. Range requests are
                 * handled by the PartialContent plugin via respondFile.
                 */
                get("${Constants.ENDPOINT_BACKGROUNDS}/asset/{slot}") {
                    if (!server.checkApiKey(call)) return@get
                    val slot = call.parameters["slot"] ?: ""
                    val isVideo = call.request.queryParameters["type"] == "video"
                    val settings = _backgroundSettings.value
                    val path = when (slot) {
                        Constants.BACKGROUND_SLOT_DEFAULT ->
                            if (isVideo) settings.defaultBackgroundVideo else settings.defaultBackgroundImage
                        Constants.BACKGROUND_SLOT_DEFAULT_LOWER_THIRD ->
                            if (isVideo) settings.defaultLowerThirdBackgroundVideo else settings.defaultLowerThirdBackgroundImage
                        Constants.BACKGROUND_SLOT_BIBLE ->
                            if (isVideo) settings.bibleBackground.backgroundVideo else settings.bibleBackground.backgroundImage
                        Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD ->
                            if (isVideo) settings.bibleLowerThirdBackground.backgroundVideo else settings.bibleLowerThirdBackground.backgroundImage
                        Constants.BACKGROUND_SLOT_SONG ->
                            if (isVideo) settings.songBackground.backgroundVideo else settings.songBackground.backgroundImage
                        Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD ->
                            if (isVideo) settings.songLowerThirdBackground.backgroundVideo else settings.songLowerThirdBackground.backgroundImage
                        else -> ""
                    }
                    if (path.isBlank()) {
                        server.logRest("/api/backgrounds/asset/{slot}", 404, "no_asset_configured_for_slot")
                        call.respond(HttpStatusCode.NotFound, "No asset configured for slot")
                        return@get
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        server.logRest("/api/backgrounds/asset/{slot}", 404, "file_not_found_on_disk")
                        call.respond(HttpStatusCode.NotFound, "Background asset not found on disk")
                        return@get
                    }
                    server.logRest("/api/backgrounds/asset/{slot}", 200)
                    call.respondFile(file)
                }

                /**
                 * POST /api/pictures/select
                 * Body: { "folder-id": "…", "index": 3, "file-name": "photo.jpg" }
                 * When "file-name" is provided the index is resolved by name so the correct
                 * image is displayed regardless of sort-order differences between clients.
                 */
                post("${Constants.ENDPOINT_PICTURES}/select") {
                    if (!server.checkApiKey(call)) return@post
                    try {
                        val req = json.decodeFromString(SelectPictureRequest.serializer(), call.receiveText())
                        // Resolve index by filename when provided — immune to sort-order mismatch
                        val resolvedIndex = req.fileName
                            ?.let { name -> _pictureFiles[req.folderId]?.indexOfFirst { it.name == name }?.takeIf { it >= 0 } }
                            ?: req.index
                        val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                        scope.launch { server.onSelectPicture.emit(req.copy(index = resolvedIndex)) }
                        val folderName = _pictureCatalogs[req.folderId]?.folderName ?: req.folderId
                        val imageLabel = req.fileName ?: "Image $resolvedIndex"
                        scope.launch { server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                            actionType = "present",
                            title = folderName,
                            detail = imageLabel,
                            clientId = clientId
                        )) }
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"invalid request body"}""")
                    }
                }

                /**
                 * POST /api/pictures/upload
                 * Body: { "name": "photo.jpg", "data": "data:image/jpeg;base64,…" }
                 *
                 * Saves the uploaded image to ~/.churchpresenter/device_uploads/,
                 * registers it as a single-image folder so the other pictures endpoints
                 * serve it, and returns { "ok": true, "folder-id": "…", "image-index": 0 }.
                 */
                post("${Constants.ENDPOINT_PICTURES}/upload") {
                    if (!server.checkApiKey(call)) return@post
                    if (!_fileUploadEnabled.value) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"file upload is disabled"}""")
                        return@post
                    }
                    try {
                        val body = call.receiveText()
                        val parsed = json.parseToJsonElement(body) as? JsonObject
                        val name = (parsed?.get("name") as? JsonPrimitive)?.content
                        val data = (parsed?.get("data") as? JsonPrimitive)?.content
                        if (name.isNullOrBlank() || data.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, """{"error":"name and data are required"}""")
                            return@post
                        }
                        val safeName = File(name).name.ifBlank { "upload.jpg" }
                        val base64Match = Regex("^data:[^;]+;base64,(.+)$").find(data)
                        if (base64Match == null) {
                            call.respond(HttpStatusCode.BadRequest, """{"error":"data must be a base64 data URI"}""")
                            return@post
                        }
                        val imageBytes = Base64.getDecoder().decode(base64Match.groupValues[1])
                        // Save to ~/.churchpresenter/device_uploads/yyyy-MM-dd/
                        // Each calendar day gets its own subfolder; the folderId includes the
                        // date so uploads from different days are catalogued separately.
                        val dateStr = java.time.LocalDate.now().toString()   // "yyyy-MM-dd"
                        val dateFolderId = "${DEVICE_UPLOADS_FOLDER_ID}_$dateStr"
                        val uploadDir = File(System.getProperty("user.home"), ".churchpresenter/device_uploads/$dateStr").also { it.mkdirs() }
                        // Ensure the file name is unique by appending a timestamp if needed
                        val uniqueName = if (File(uploadDir, safeName).exists()) {
                            val ts = System.currentTimeMillis()
                            val ext = safeName.substringAfterLast('.', "jpg")
                            val base = safeName.substringBeforeLast('.', safeName)
                            "${base}_$ts.$ext"
                        } else safeName
                        val file = File(uploadDir, uniqueName)
                        file.writeBytes(imageBytes)
                        // Accumulate into today's dated "Device Photos" folder.
                        // Sort by filename so the catalog index order matches the desktop's
                        // PicturesViewModel.loadImagesFromFolder which also sorts by name.
                        // Without this, upload order ≠ filename order, so index N on mobile
                        // points to a different photo than index N on the desktop.
                        val existingFiles = (_pictureFiles[dateFolderId] ?: emptyList()).toMutableList()
                        existingFiles.add(file)
                        existingFiles.sortBy { it.name }          // ← match desktop sort order
                        val newIndex = existingFiles.indexOf(file) // recalculate after sort
                        _pictureFiles[dateFolderId] = existingFiles
                        val catalog = PictureFolderResponse(
                            folderId   = dateFolderId,
                            folderName = "Device Photos ($dateStr)",
                            folderPath = uploadDir.absolutePath,
                            imageTotal = existingFiles.size,
                            images     = existingFiles.mapIndexed { idx, f ->
                                PictureFileDto(
                                    index        = idx,
                                    fileName     = f.name,
                                    thumbnailUrl = "${Constants.ENDPOINT_PICTURES}/$dateFolderId/images/$idx"
                                )
                            }
                        )
                        _pictureCatalogs[dateFolderId] = catalog
                        // Do NOT update _pictureCatalog here — that would replace the desktop's
                        // active folder with device_uploads, making GET /api/pictures return the
                        // wrong folder to the mobile companion app.
                        server.broadcast(WebSocketMessage(
                            type    = Constants.WS_EVENT_PICTURES_UPDATED,
                            payload = json.encodeToString(PictureFolderResponse.serializer(), catalog)
                        ))
                        val picUploadClientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                        scope.launch { server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                            actionType = "upload",
                            title = file.name,
                            detail = catalog.folderName,
                            clientId = picUploadClientId
                        )) }
                        call.respondText(
                            """{"ok":true,"folder-id":"$dateFolderId","image-index":$newIndex,"file-name":"${file.name}"}""",
                            ContentType.Application.Json
                        )
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, """{"error":"upload failed: ${e.message?.replace("\"","\\\"")}"}""")
                    }
                }

}
