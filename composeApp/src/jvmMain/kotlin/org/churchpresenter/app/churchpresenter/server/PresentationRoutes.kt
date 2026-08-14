package org.churchpresenter.app.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.receiveStream
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.churchpresenter.app.churchpresenter.utils.Constants

/**
 * Routes serving presentation catalogues and rendered slide images.
 *
 * Body moved verbatim from `CompanionServer` — raw-string literals make the indentation
 * load-bearing. The slide caches arrive as identically-named parameters and stay private to the
 * server otherwise.
 */
internal fun Route.presentationRoutes(
    server: CompanionServer,
    _fileUploadEnabled: MutableStateFlow<Boolean>,
    _maxMediaUploadMb: MutableStateFlow<Int>,
    _presentationCatalog: MutableStateFlow<PresentationCatalogResponse>,
    _presentationCatalogs: ConcurrentHashMap<String, PresentationDto>,
    _presentationFilePaths: ConcurrentHashMap<String, String>,
    _scheduleItemToPresentationId: ConcurrentHashMap<String, String>,
    _slideBytes: ConcurrentHashMap<String, List<ByteArray>>,
    json: Json,
    scope: CoroutineScope,
) {
                get(Constants.ENDPOINT_PRESENTATIONS) {
                    if (!server.checkApiKey(call)) return@get
                    call.respond(_presentationCatalog.value)
                }

                /**
                 * GET /api/presentations/{id}
                 * Returns metadata for a specific presentation by its ID.
                 *
                 * The {id} is either:
                 *  - the schedule item UUID from GET /api/schedule (works for every presentation
                 *    item as soon as the schedule is received — slides are rendered in the background), or
                 *  - the presentation file hash returned by GET /api/presentations.
                 *
                 * Returns 404 while background rendering is still in progress — retry after a moment.
                 */
                get("${Constants.ENDPOINT_PRESENTATIONS}/{id}") {
                    if (!server.checkApiKey(call)) return@get
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, "Missing id")
                        return@get
                    }
                    val resolvedId = _scheduleItemToPresentationId[id] ?: id
                    val dto = _presentationCatalogs[resolvedId]
                    if (dto == null) {
                        server.logRest("/api/presentations/{id}", 404, "not_found_or_not_yet_rendered")
                        call.respond(HttpStatusCode.NotFound, "Presentation not found or not yet rendered")
                        return@get
                    }
                    server.logRest("/api/presentations/{id}", 200)
                    call.respond(dto)
                }

                /**
                 * GET /api/presentations/{id}/slides/{index}
                 * Returns the slide at {index} as a JPEG image for the presentation with {id}.
                 */
                get("${Constants.ENDPOINT_PRESENTATIONS}/{id}/slides/{index}") {
                    if (!server.checkApiKey(call)) return@get
                    val id    = call.parameters["id"]    ?: run { call.respond(HttpStatusCode.BadRequest, "Missing id"); return@get }
                    val index = call.parameters["index"]?.toIntOrNull() ?: run { call.respond(HttpStatusCode.BadRequest, "Invalid index"); return@get }
                    val resolvedId = _scheduleItemToPresentationId[id] ?: id
                    val slides = _slideBytes[resolvedId]
                    if (slides == null) {
                        server.logRest("/api/presentations/{id}/slides/{index}", 404, "presentation_not_found")
                        call.respond(HttpStatusCode.NotFound, "Presentation not found")
                        return@get
                    }
                    if (index < 0 || index >= slides.size) {
                        server.logRest("/api/presentations/{id}/slides/{index}", 404, "slide_index_out_of_range")
                        call.respond(HttpStatusCode.NotFound, "Slide index out of range")
                        return@get
                    }
                    server.logRest("/api/presentations/{id}/slides/{index}", 200)
                    call.respondBytes(slides[index], ContentType.Image.JPEG)
                }

                /**
                 * POST /api/presentations/{id}/select
                 * Body: { "index": 2 }
                 *
                 * Instantly navigates the live presentation to slide [index] (0-based).
                 * No approval dialog — fires immediately like select_picture.
                 * The {id} is the presentation file hash or schedule item UUID.
                 * Response: {"ok":true}
                 */
                post("${Constants.ENDPOINT_PRESENTATIONS}/{id}/select") {
                    if (!server.checkApiKey(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val index = call.request.queryParameters["index"]?.toIntOrNull()
                        ?: runCatching {
                            json.decodeFromString(SelectSlideRequest.serializer(), call.receiveText()).index
                        }.getOrNull()
                    if (index == null || index < 0) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing or invalid index"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    scope.launch { server.onSelectSlide.emit(SelectSlideRequest(id = id, index = index)) }
                    val presentationName = _presentationCatalogs[_scheduleItemToPresentationId[id] ?: id]?.fileName ?: id
                    scope.launch { server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                        actionType = "present",
                        title = presentationName,
                        detail = "Slide ${index + 1}",
                        clientId = clientId
                    )) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                /**
                 * POST /api/presentations/upload
                 * Body: { "name": "slides.pdf", "data": "data:application/pdf;base64,…" }
                 *
                 * Decodes the base64 data-URI, saves the file to
                 * ~/.churchpresenter/device_presentations/, and emits [server.onPresentationUploaded]
                 * so the desktop can load it into PresentationViewModel.
                 *
                 * Response: { "ok": true, "id": "<hex-hash>", "name": "<fileName>" }
                 */
                post("${Constants.ENDPOINT_PRESENTATIONS}/upload") {
                    if (!server.checkApiKey(call)) return@post
                    if (!_fileUploadEnabled.value) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"file upload is disabled"}""")
                        return@post
                    }
                    try {
                        val contentLength = call.request.headers["Content-Length"]?.toLongOrNull() ?: 0L
                        if (contentLength > 200 * 1024 * 1024) { // 200 MB limit
                            call.respond(HttpStatusCode.PayloadTooLarge, """{"error":"file too large (max 200 MB)"}""")
                            return@post
                        }
                        val body   = call.receiveText()
                        val parsed = json.parseToJsonElement(body) as? JsonObject
                        val name   = (parsed?.get("name") as? JsonPrimitive)?.content
                        val data   = (parsed?.get("data") as? JsonPrimitive)?.content
                        if (name.isNullOrBlank() || data.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, """{"error":"name and data are required"}""")
                            return@post
                        }
                        val safeName = File(name).name.ifBlank { "upload.pdf" }
                        val ext = safeName.substringAfterLast('.', "").lowercase()
                        if (ext !in setOf("pdf", "ppt", "pptx", "key")) {
                            call.respond(HttpStatusCode.UnsupportedMediaType, """{"error":"unsupported file type: $ext"}""")
                            return@post
                        }
                        val base64Match = Regex("^data:[^;]+;base64,(.+)$").find(data)
                        if (base64Match == null) {
                            call.respond(HttpStatusCode.BadRequest, """{"error":"data must be a base64 data URI"}""")
                            return@post
                        }
                        val fileBytes = Base64.getDecoder().decode(base64Match.groupValues[1])
                        val uploadDir = File(System.getProperty("user.home"),
                            ".churchpresenter/device_presentations").also { it.mkdirs() }
                        val uniqueName = if (File(uploadDir, safeName).exists()) {
                            val ts   = System.currentTimeMillis()
                            val base = safeName.substringBeforeLast('.', safeName)
                            "${base}_$ts.$ext"
                        } else safeName
                        val file = File(uploadDir, uniqueName)
                        file.writeBytes(fileBytes)
                        val id = file.absolutePath.hashCode().toUInt().toString(16)
                        // Evict the previous device-uploaded presentation so the mobile list
                        // never accumulates stale entries — only the latest upload is shown.
                        server.presentations._lastDeviceUploadedPresentationId?.let { oldId ->
                            _presentationCatalogs.remove(oldId)
                            _slideBytes.remove(oldId)
                            _presentationFilePaths.remove(oldId)
                        }
                        server.presentations._lastDeviceUploadedPresentationId = id
                        val uploadClientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                        scope.launch { server.onPresentationUploaded.emit(file) }
                        scope.launch { server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                            actionType = "upload",
                            title = file.name,
                            detail = "${fileBytes.size / 1024} KB",
                            clientId = uploadClientId
                        )) }
                        call.respondText(
                            """{"ok":true,"id":"$id","name":"${file.nameWithoutExtension.replace("\"", "\\\"")}"}""",
                            ContentType.Application.Json
                        )
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, """{"error":"upload failed: ${e.message?.replace("\"", "\\\"")}"}""")
                    }
                }

                /**
                 * POST /api/media/upload?name=clip.mp4
                 * Body: the raw file bytes (application/octet-stream), streamed straight to disk.
                 *
                 * Streaming (rather than a base64 JSON body) keeps memory flat on both ends so
                 * large video files don't OOM the phone or the desktop. Saves to
                 * ~/.churchpresenter/device_media/ and returns the absolute path so the companion
                 * can Go Live / Add to Schedule a local MediaItem pointing at it.
                 *
                 * Response: { "ok": true, "path": "<abs path>", "name": "<title>", "mediaType": "local|audio" }
                 */
                post(Constants.ENDPOINT_MEDIA_UPLOAD) {
                    if (!server.checkApiKey(call)) return@post
                    if (!_fileUploadEnabled.value) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"file upload is disabled"}""")
                        return@post
                    }
                    try {
                        val maxBytes = _maxMediaUploadMb.value.toLong() * 1024 * 1024
                        val contentLength = call.request.headers["Content-Length"]?.toLongOrNull() ?: 0L
                        if (contentLength > maxBytes) {
                            call.respond(HttpStatusCode.PayloadTooLarge, """{"error":"file too large (max ${_maxMediaUploadMb.value} MB)"}""")
                            return@post
                        }
                        val rawName = call.request.queryParameters["name"]
                        if (rawName.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest, """{"error":"name query parameter is required"}""")
                            return@post
                        }
                        val safeName = File(rawName).name.ifBlank { "upload.mp4" }
                        val ext = safeName.substringAfterLast('.', "").lowercase()
                        // Accept exactly what the desktop media player (VLC) can play.
                        if (ext !in Constants.VIDEO_EXTENSIONS && ext !in Constants.AUDIO_EXTENSIONS) {
                            call.respond(HttpStatusCode.UnsupportedMediaType, """{"error":"unsupported file type: $ext"}""")
                            return@post
                        }
                        val uploadDir = File(System.getProperty("user.home"),
                            ".churchpresenter/device_media").also { it.mkdirs() }
                        val uniqueName = if (File(uploadDir, safeName).exists()) {
                            val ts   = System.currentTimeMillis()
                            val base = safeName.substringBeforeLast('.', safeName)
                            "${base}_$ts.$ext"
                        } else safeName
                        val file = File(uploadDir, uniqueName)
                        // Stream the request body to disk with a fixed buffer (constant memory).
                        val written = withContext(Dispatchers.IO) {
                            call.receiveStream().use { input ->
                                file.outputStream().use { out -> input.copyTo(out, bufferSize = 1 shl 20) }
                            }
                        }
                        val mediaType = if (ext in Constants.AUDIO_EXTENSIONS) Constants.MEDIA_TYPE_AUDIO else Constants.MEDIA_TYPE_LOCAL
                        val uploadClientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                        scope.launch { server.onInstantAction.emit(CompanionServer.RemoteInstantAction(
                            actionType = "upload",
                            title = file.name,
                            detail = "${written / 1024} KB",
                            clientId = uploadClientId
                        )) }
                        val escapedPath = file.absolutePath.replace("\\", "\\\\").replace("\"", "\\\"")
                        call.respondText(
                            """{"ok":true,"path":"$escapedPath","name":"${file.nameWithoutExtension.replace("\"", "\\\"")}","mediaType":"$mediaType"}""",
                            ContentType.Application.Json
                        )
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, """{"error":"upload failed: ${e.message?.replace("\"", "\\\"")}"}""")
                    }
                }

                // ── Presentation remote control endpoints ─────────────────────

                /** GET /presentation-remote — mobile remote control web page */
}
