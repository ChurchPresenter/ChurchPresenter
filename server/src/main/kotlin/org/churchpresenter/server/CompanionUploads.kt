package org.churchpresenter.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import java.io.File
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants

private const val MAX_UPLOAD_MB = 200
private const val BYTES_PER_MB = 1024 * 1024
private val UPLOADABLE_EXTENSIONS = setOf("pdf", "ppt", "pptx", "key")

/*
 * Items sent from a phone to the schedule, and presentation files uploaded to the desktop.
 *
 * Functions of [CompanionServer], kept beside it rather than in it so no one file holds all of
 * its behaviour; they read and write the server's own state.
 */

/**
 * Try to parse a [ScheduleItem] from raw JSON using the flat [RemoteItemRequest] format first,
 * then fall back to the legacy sealed-class [AddToScheduleRequest] format.
 */
internal fun CompanionServer.parseRemoteItem(body: String): ScheduleItem? =
    // 1. flat format: {"item":{"songNumber":42,"title":"…","songbook":"…"}}
    runCatching { parseFlatRemoteItem(body) }.getOrNull()
    // 2. legacy sealed-class format with discriminator
        ?: runCatching { json.decodeFromString(AddToScheduleRequest.serializer(), body).item }.getOrNull()

private fun CompanionServer.parseFlatRemoteItem(body: String): ScheduleItem? {
    val dto = json.decodeFromString(RemoteItemRequest.serializer(), body).item
    return pictureItemFor(dto) ?: presentationItemFor(dto) ?: dto.toScheduleItem()
}

// Picture identified by folder-id (companion app format): folderPath is null in that case —
// resolve it via the cached catalog.
private fun CompanionServer.pictureItemFor(dto: RemoteItemDto): ScheduleItem.PictureItem? {
    if (dto.folderId == null || dto.folderPath != null) return null
    val catalog = pictures.catalogs[dto.folderId] ?: return null
    return ScheduleItem.PictureItem(
        id         = dto.id.ifBlank { java.util.UUID.randomUUID().toString() },
        folderPath = catalog.folderPath,
        folderName = catalog.folderName,
        imageCount = catalog.imageTotal
    )
}

// Presentation identified by id/fileHash (companion app format): filePath is not sent — resolve
// via presentations._presentationFilePaths (populated by updatePresentation and updateSchedule)
// then fall back to a _schedule scan.
// NOTE: mobile may omit the "type" field when it equals the default ("presentation"), so also
// accept type==null as long as the id resolves in presentations._presentationFilePaths.
private fun CompanionServer.presentationItemFor(dto: RemoteItemDto): ScheduleItem.PresentationItem? {
    val looksLikePresentation = dto.type == "presentation" || dto.type == null
    val noExplicitTarget = dto.filePath == null && dto.folderId == null
    if (!noExplicitTarget || dto.id.isBlank() || !looksLikePresentation) return null
    val filePath = presentations._presentationFilePaths[dto.id]
        ?: _schedule.value.firstOrNull { s ->
            s.type == "presentation" && (
                s.id == dto.id ||
                s.filePath?.hashCode()?.toUInt()?.toString(16) == dto.id
            )
        }?.filePath
        ?: return null
    val catalog = presentations._presentationCatalogs[dto.id]
    return ScheduleItem.PresentationItem(
        id         = java.util.UUID.randomUUID().toString(),
        filePath   = filePath,
        fileName   = catalog?.fileName ?: dto.title ?: "",
        slideCount = catalog?.slideTotal ?: 0,
        fileType   = catalog?.fileType ?: ""
    )
}

/** The uploaded file's safe name and bytes, or null once the rejection has been responded with. */
private suspend fun CompanionServer.receiveUploadedFile(call: ApplicationCall): Pair<String, ByteArray>? {
    val contentLength = call.request.headers["Content-Length"]?.toLongOrNull() ?: 0L
    if (contentLength > MAX_UPLOAD_MB * BYTES_PER_MB) {
        call.respond(HttpStatusCode.PayloadTooLarge, """{"error":"file too large (max 200 MB)"}""")
        return null
    }
    val parsed = json.parseToJsonElement(call.receiveText()) as? JsonObject
    val name   = (parsed?.get("name") as? JsonPrimitive)?.content
    val data   = (parsed?.get("data") as? JsonPrimitive)?.content
    if (name.isNullOrBlank() || data.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, """{"error":"name and data are required"}""")
        return null
    }
    return decodeUploadedFile(call, name, data)
}

private suspend fun CompanionServer.decodeUploadedFile(
    call: ApplicationCall,
    name: String,
    data: String
): Pair<String, ByteArray>? {
    val safeName = File(name).name.ifBlank { "upload.pdf" }
    val ext = safeName.substringAfterLast('.', "").lowercase()
    if (ext !in UPLOADABLE_EXTENSIONS) {
        call.respond(HttpStatusCode.UnsupportedMediaType, """{"error":"unsupported file type: $ext"}""")
        return null
    }
    val base64Match = Regex("^data:[^;]+;base64,(.+)$").find(data)
    if (base64Match == null) {
        call.respond(HttpStatusCode.BadRequest, """{"error":"data must be a base64 data URI"}""")
        return null
    }
    return safeName to Base64.getDecoder().decode(base64Match.groupValues[1])
}

internal suspend fun CompanionServer.handlePresentationFileUpload(call: ApplicationCall) {
    try {
        val (safeName, fileBytes) = receiveUploadedFile(call) ?: return
        val ext = safeName.substringAfterLast('.', "").lowercase()
        val uploadDir = deviceUploadDir.also { it.mkdirs() }
        val uniqueName = if (File(uploadDir, safeName).exists()) {
            val ts   = System.currentTimeMillis()
            val base = safeName.substringBeforeLast('.', safeName)
            "${base}_$ts.$ext"
        } else safeName
        val file = File(uploadDir, uniqueName)
        file.writeBytes(fileBytes)
        pruneDeviceUploads()
        val id = file.absolutePath.hashCode().toUInt().toString(16)
        presentations.evictPreviousDeviceUpload()
        presentations._presentationFilePaths[id] = file.absolutePath
        presentations._lastDeviceUploadedPresentationId = id
        val uploadClientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
        scope.launch { onPresentationUploaded.emit(file) }
        scope.launch { onInstantAction.emit(CompanionServer.RemoteInstantAction(
            actionType = "upload",
            title = RemoteLabel.Text(file.name),
            detail = RemoteLabel.Size(fileBytes.size.toLong()),
            clientId = uploadClientId
        )) }
        call.respondText(
            """{"ok":true,"id":"$id","name":"${file.nameWithoutExtension.replace("\"", "\\\"")}"}""",
            ContentType.Application.Json
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        // The client went away mid-upload, or the file could not be written.
        uploadFailed(call, e)
    } catch (e: IllegalArgumentException) {
        // A body that is not JSON, or data that is not valid base64.
        uploadFailed(call, e)
    }
}

private suspend fun CompanionServer.uploadFailed(call: ApplicationCall, e: Exception) {
    call.respond(
        HttpStatusCode.InternalServerError,
        """{"error":"upload failed: ${e.message?.replace("\"", "\\\"")}"}"""
    )
}
