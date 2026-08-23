package org.churchpresenter.companionserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.engine.BaseApplicationResponse
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.writeFully
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants

/** nginx's convention for a request the client abandoned before its response finished. */
private const val STATUS_CLIENT_CLOSED_REQUEST = 499

private const val FILE_COPY_BUFFER_BYTES = 64 * 1024

/**
 * Runs [send], treating a download the client walks away from as the ordinary event it is.
 *
 * A phone that closes the app, leaves Wi-Fi or cancels mid-file ends the response with fewer bytes
 * written than the `Content-Length` already on the wire, and Ktor raises
 * [BaseApplicationResponse.BodyLengthIsTooSmall] — or an [IOException] off the dead socket — for it.
 * Uncaught, that reaches `StatusPages`, which tries to write "Internal server error" into a response
 * that is already committed and reports the whole thing as a server fault. Nothing can be retried
 * and nothing more can be sent, so it is recorded against [endpoint] and dropped.
 *
 * Anything else — a missing file, a fault in our own code — still escapes and is reported.
 */
internal suspend fun sendOrDropOnClientExit(server: CompanionServer, endpoint: String, send: suspend () -> Unit) {
    try {
        send()
    } catch (_: BaseApplicationResponse.BodyLengthIsTooSmall) {
        server.logRest(endpoint, STATUS_CLIENT_CLOSED_REQUEST, "download_ended_early")
    } catch (_: IOException) {
        server.logRest(endpoint, STATUS_CLIENT_CLOSED_REQUEST, "download_connection_lost")
    }
}

/** [respondFile] under [sendOrDropOnClientExit]'s rule — every file this server streams goes out this way. */
private suspend fun ApplicationCall.respondFileOrDrop(server: CompanionServer, endpoint: String, file: File) =
    sendOrDropOnClientExit(server, endpoint) { respondFile(file) }

/**
 * Streams a Bible module from a handle opened before a byte of the response is committed.
 *
 * [respondFile] opens the file lazily, from inside a coroutine completion handler — so a module
 * that is deleted between the `exists()` check and that open does not fail the request, it throws
 * `FileNotFoundException` where nothing can catch it and the app records a fatal crash. A follower's
 * own Bible cache is exactly such a file: `invalidateInstanceLinkBibleCache` deletes it whenever the
 * link's translations change, and a second follower may be downloading it at that moment.
 *
 * Opening first collapses that race into an ordinary 404, and pins the bytes for the rest of the
 * response. Range requests are not served here — every Bible fetch, mobile or follower, asks for the
 * whole module — so this gives up nothing `PartialContent` was doing.
 */
private suspend fun ApplicationCall.respondBibleFile(server: CompanionServer, endpoint: String, file: File) {
    val stream = try {
        file.inputStream()
    } catch (_: FileNotFoundException) {
        server.logRest(endpoint, HttpStatusCode.NotFound.value, "file_vanished_before_send")
        respond(HttpStatusCode.NotFound, "Bible file not found on disk")
        return
    }
    server.logRest(endpoint, HttpStatusCode.OK.value)
    sendOrDropOnClientExit(server, endpoint) {
        stream.use { open ->
            respondBytesWriter(ContentType.Application.OctetStream, contentLength = file.length()) {
                val buffer = ByteArray(FILE_COPY_BUFFER_BYTES)
                while (true) {
                    val read = open.read(buffer)
                    if (read <= 0) break
                    writeFully(buffer, 0, read)
                }
                flush()
            }
        }
    }
}

/**
 * Routes serving pictures, uploaded media and background assets.
 *
 * Body moved verbatim from `CompanionServer` — raw-string literals make the indentation
 * load-bearing. Picture caches arrive as identically-named parameters; the Bible file paths are
 * mutable and reached through [server].
 */
internal fun Route.mediaAndAssetRoutes(
    server: CompanionServer,
    deviceUploadsFolderId: String,
    _backgroundSettings: MutableStateFlow<BackgroundSettings>,
    _fileUploadEnabled: MutableStateFlow<Boolean>,
    _pictureCatalog: MutableStateFlow<PictureFolderResponse?>,
    _pictureCatalogs: ConcurrentHashMap<String, PictureFolderResponse>,
    _pictureFiles: ConcurrentHashMap<String, List<File>>,
    _scheduleItemToMediaPath: ConcurrentHashMap<String, String>,
    json: Json,
    scope: CoroutineScope,
) {
    pictureRoutes(server, _pictureCatalog, _pictureCatalogs, _pictureFiles)
    mediaStreamAndBibleFileRoutes(server, _scheduleItemToMediaPath)
    backgroundAndPictureUploadRoutes(
        server, deviceUploadsFolderId, _backgroundSettings, _fileUploadEnabled,
        _pictureCatalogs, _pictureFiles, json, scope
    )
}

private fun Route.pictureRoutes(
    server: CompanionServer,
    _pictureCatalog: MutableStateFlow<PictureFolderResponse?>,
    _pictureCatalogs: ConcurrentHashMap<String, PictureFolderResponse>,
    _pictureFiles: ConcurrentHashMap<String, List<File>>,
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
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Missing id")
                        return@get
                    }
                    val catalog = _pictureCatalogs[id]
                    if (catalog == null) {
                        server.logRest("/api/pictures/{id}", HttpStatusCode.NotFound.value, "folder_not_found")
                        call.respond(HttpStatusCode.NotFound, "Picture folder not found")
                        return@get
                    }
                    server.logRest("/api/pictures/{id}", HttpStatusCode.OK.value)
                    call.respond(catalog)
                }

                /**
                 * GET /api/pictures/{id}/images/{index}
                 * Returns the image at {index} as a JPEG for the folder with {id}.
                 */
                get("${Constants.ENDPOINT_PICTURES}/{id}/images/{index}") {
                    if (!server.checkApiKey(call)) return@get
                    val id    = call.parameters["id"]    ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Missing id")
                        return@get
                    }
                    val index = call.parameters["index"]?.toIntOrNull() ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Invalid index")
                        return@get
                    }
                    val files = _pictureFiles[id]
                    if (files == null) {
                        server.logRest(
                            "/api/pictures/{id}/images/{index}",
                            HttpStatusCode.NotFound.value,
                            "folder_not_found",
                        )
                        call.respond(HttpStatusCode.NotFound, "Picture folder not found")
                        return@get
                    }
                    if (index < 0 || index >= files.size) {
                        server.logRest(
                            "/api/pictures/{id}/images/{index}",
                            HttpStatusCode.NotFound.value,
                            "index_out_of_range",
                        )
                        call.respond(HttpStatusCode.NotFound, "Image index out of range")
                        return@get
                    }
                    val file = files[index]
                    if (!file.exists()) {
                        server.logRest(
                            "/api/pictures/{id}/images/{index}",
                            HttpStatusCode.NotFound.value,
                            "file_not_found_on_disk",
                        )
                        call.respond(HttpStatusCode.NotFound, "Image file not found on disk")
                        return@get
                    }
                    // HEIC/HEIF are not displayable by browsers — convert to JPEG first
                    val ext = file.extension.lowercase()
                    if (ext == "heic" || ext == "heif") {
                        val jpegBytes = server.host.decodeHeicToJpeg(file)
                        if (jpegBytes != null) {
                            server.logRest("/api/pictures/{id}/images/{index}", HttpStatusCode.OK.value)
                            call.respondBytes(jpegBytes, ContentType.Image.JPEG)
                        } else {
                            server.logRest(
                                "/api/pictures/{id}/images/{index}",
                                HttpStatusCode.InternalServerError.value,
                                "heic_conversion_failed",
                            )
                            call.respond(HttpStatusCode.InternalServerError, "Failed to convert HEIC image")
                        }
                    } else {
                        server.logRest("/api/pictures/{id}/images/{index}", HttpStatusCode.OK.value)
                        call.respondBytes(file.readBytes(), contentTypeForExtension(ext))
                    }
                }

                /**
                 * GET /api/media/stream/{id} — streams a local media file's raw bytes for a schedule
                 * item registered via [server.updateSchedule] (mediaType == "local"). Range requests are
                 * handled by the PartialContent plugin so seeking works, letting an InstanceLink
                 * follower play the file over HTTP without needing a local copy.
                 */
}

private fun Route.mediaStreamAndBibleFileRoutes(
    server: CompanionServer,
    _scheduleItemToMediaPath: ConcurrentHashMap<String, String>,
) {
                get("${Constants.ENDPOINT_MEDIA_STREAM}/{id}") {
                    if (!server.checkApiKey(call)) return@get
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Missing id")
                        return@get
                    }
                    val path = _scheduleItemToMediaPath[id]
                    if (path == null) {
                        server.logRest("/api/media/stream/{id}", HttpStatusCode.NotFound.value, "media_item_not_found")
                        call.respond(HttpStatusCode.NotFound, "Media item not found")
                        return@get
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        server.logRest(
                            "/api/media/stream/{id}",
                            HttpStatusCode.NotFound.value,
                            "file_not_found_on_disk",
                        )
                        call.respond(HttpStatusCode.NotFound, "Media file not found on disk")
                        return@get
                    }
                    server.logRest("/api/media/stream/{id}", HttpStatusCode.OK.value)
                    call.respondFileOrDrop(server, "/api/media/stream/{id}", file)
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
                        server.logRest("/api/bible/file", HttpStatusCode.NotFound.value, "no_bible_loaded")
                        call.respond(HttpStatusCode.NotFound, "No bible loaded")
                        return@get
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        server.logRest("/api/bible/file", HttpStatusCode.NotFound.value, "file_not_found_on_disk")
                        call.respond(HttpStatusCode.NotFound, "Bible file not found on disk")
                        return@get
                    }
                    call.respondBibleFile(server, "/api/bible/file", file)
                }

                /** GET /api/bible/file/secondary — same as above, for a follower that opted in to
                 *  mirroring the primary's secondary bible instead of keeping its own. */
                get("${Constants.ENDPOINT_BIBLE_FILE}/secondary") {
                    if (!server.checkApiKey(call)) return@get
                    val path = server._secondaryBibleFilePath
                    if (path.isEmpty()) {
                        server.logRest(
                            "/api/bible/file/secondary",
                            HttpStatusCode.NotFound.value,
                            "no_secondary_bible_loaded",
                        )
                        call.respond(HttpStatusCode.NotFound, "No secondary bible loaded")
                        return@get
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        server.logRest(
                            "/api/bible/file/secondary",
                            HttpStatusCode.NotFound.value,
                            "file_not_found_on_disk",
                        )
                        call.respond(HttpStatusCode.NotFound, "Secondary bible file not found on disk")
                        return@get
                    }
                    call.respondBibleFile(server, "/api/bible/file/secondary", file)
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
                        server.logRest(
                            "/api/bible/file/translation/{index}",
                            HttpStatusCode.NotFound.value,
                            "translation_not_found",
                        )
                        call.respond(HttpStatusCode.NotFound, "Bible translation not found")
                        return@get
                    }
                    call.respondBibleFile(server, "/api/bible/file/translation/{index}", File(path))
                }

                /** GET /api/backgrounds — current BackgroundSettings as JSON. Image/video fields are
                 *  still local file paths on this machine; a follower resolves the actual bytes via
                 *  GET /api/backgrounds/asset/{slot} below, keyed by slot rather than raw path. */
}

private fun Route.backgroundAndPictureUploadRoutes(
    server: CompanionServer,
    deviceUploadsFolderId: String,
    _backgroundSettings: MutableStateFlow<BackgroundSettings>,
    _fileUploadEnabled: MutableStateFlow<Boolean>,
    _pictureCatalogs: ConcurrentHashMap<String, PictureFolderResponse>,
    _pictureFiles: ConcurrentHashMap<String, List<File>>,
    json: Json,
    scope: CoroutineScope,
) {
                get(Constants.ENDPOINT_BACKGROUNDS) {
                    if (!server.checkApiKey(call)) return@get
                    server.logRest("/api/backgrounds", HttpStatusCode.OK.value)
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
                            if (isVideo) settings.defaultLowerThirdBackgroundVideo
                            else settings.defaultLowerThirdBackgroundImage
                        Constants.BACKGROUND_SLOT_BIBLE ->
                            if (isVideo) settings.bibleBackground.backgroundVideo
                            else settings.bibleBackground.backgroundImage
                        Constants.BACKGROUND_SLOT_BIBLE_LOWER_THIRD ->
                            if (isVideo) settings.bibleLowerThirdBackground.backgroundVideo
                            else settings.bibleLowerThirdBackground.backgroundImage
                        Constants.BACKGROUND_SLOT_SONG ->
                            if (isVideo) settings.songBackground.backgroundVideo
                            else settings.songBackground.backgroundImage
                        Constants.BACKGROUND_SLOT_SONG_LOWER_THIRD ->
                            if (isVideo) settings.songLowerThirdBackground.backgroundVideo
                            else settings.songLowerThirdBackground.backgroundImage
                        else -> ""
                    }
                    if (path.isBlank()) {
                        server.logRest(
                            "/api/backgrounds/asset/{slot}",
                            HttpStatusCode.NotFound.value,
                            "no_asset_configured_for_slot",
                        )
                        call.respond(HttpStatusCode.NotFound, "No asset configured for slot")
                        return@get
                    }
                    val file = File(path)
                    if (!file.exists()) {
                        server.logRest(
                            "/api/backgrounds/asset/{slot}",
                            HttpStatusCode.NotFound.value,
                            "file_not_found_on_disk",
                        )
                        call.respond(HttpStatusCode.NotFound, "Background asset not found on disk")
                        return@get
                    }
                    server.logRest("/api/backgrounds/asset/{slot}", HttpStatusCode.OK.value)
                    call.respondFileOrDrop(server, "/api/backgrounds/asset/{slot}", file)
                }

                /**
                 * POST /api/pictures/select
                 * Body: { "folder-id": "…", "index": 3, "file-name": "photo.jpg" }
                 * When "file-name" is provided the index is resolved by name so the correct
                 * image is displayed regardless of sort-order differences between clients.
                 */
    pictureSelectAndUploadRoutes(
        server,
        deviceUploadsFolderId,
        _fileUploadEnabled,
        _pictureCatalogs,
        _pictureFiles,
        json,
        scope,
    )
}

private fun Route.pictureSelectAndUploadRoutes(
    server: CompanionServer,
    deviceUploadsFolderId: String,
    _fileUploadEnabled: MutableStateFlow<Boolean>,
    _pictureCatalogs: ConcurrentHashMap<String, PictureFolderResponse>,
    _pictureFiles: ConcurrentHashMap<String, List<File>>,
    json: Json,
    scope: CoroutineScope,
) {
                post("${Constants.ENDPOINT_PICTURES}/select") {
                    if (!server.allowsRequest(call)) return@post
                    try {
                        val req = json.decodeFromString(SelectPictureRequest.serializer(), call.receiveText())
                        // Resolve index by filename when provided — immune to sort-order mismatch
                        val resolvedIndex = req.fileName?.let { name ->
                            _pictureFiles[req.folderId]?.indexOfFirst { it.name == name }?.takeIf { it >= 0 }
                        } ?: req.index
                        val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                        val folderName = _pictureCatalogs[req.folderId]?.folderName ?: req.folderId
                        val imageLabel = req.fileName ?: "Image $resolvedIndex"
                        if (!server.requestApproval("present", folderName, imageLabel, clientId)) {
                            call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                            return@post
                        }
                        scope.launch { server.onSelectPicture.emit(req.copy(index = resolvedIndex)) }
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
                    if (!server.allowsRequest(call)) return@post
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
                        val uploadClientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                        if (!server.requestApproval("upload", safeName, "", uploadClientId)) {
                            call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                            return@post
                        }
                        // Save to ~/.churchpresenter/device_uploads/yyyy-MM-dd/
                        // Each calendar day gets its own subfolder; the folderId includes the
                        // date so uploads from different days are catalogued separately.
                        val dateStr = java.time.LocalDate.now().toString()   // "yyyy-MM-dd"
                        val dateFolderId = "${deviceUploadsFolderId}_$dateStr"
                        val file = writeDeviceUpload(safeName, dateStr, imageBytes)
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
                            folderPath = file.parentFile.absolutePath,
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
                            """{"ok":true,"folder-id":"$dateFolderId","image-index":$newIndex,""" +
                                """"file-name":"${file.name}"}""",
                            ContentType.Application.Json
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            """{"error":"upload failed: ${e.message?.replace("\"", "\\\"")}"}""",
                        )
                    }
                }
}

/**
 * Writes one uploaded photo into today's `~/.churchpresenter/device_uploads/<date>/` and returns the
 * file it wrote.
 *
 * Each calendar day gets its own subfolder, and a name already taken there gains a timestamp rather
 * than overwriting — two phones sending `IMG_0001.jpg` in the same service must both survive.
 */
private fun writeDeviceUpload(safeName: String, dateStr: String, bytes: ByteArray): File {
    val uploadDir = File(
        System.getProperty("user.home"), ".churchpresenter/device_uploads/$dateStr"
    ).also { it.mkdirs() }
    val uniqueName = if (File(uploadDir, safeName).exists()) {
        val ts = System.currentTimeMillis()
        val ext = safeName.substringAfterLast('.', "jpg")
        val base = safeName.substringBeforeLast('.', safeName)
        "${base}_$ts.$ext"
    } else safeName
    return File(uploadDir, uniqueName).also { it.writeBytes(bytes) }
}
