package org.churchpresenter.companionserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants

/**
 * Routes for server info/status and the song catalogue.
 *
 * Body moved verbatim from `CompanionServer` — raw-string literals make the indentation
 * load-bearing. Private state arrives as identically-named parameters; [server] carries what must
 * be read per request.
 */
internal fun Route.infoAndSongRoutes(
    server: CompanionServer,
    _bibleCatalog: MutableStateFlow<BibleCatalogResponse?>,
    _catalog: MutableStateFlow<SongCatalogResponse>,
    _fileUploadEnabled: MutableStateFlow<Boolean>,
    _maxMediaUploadMb: MutableStateFlow<Int>,
    json: Json,
    scope: CoroutineScope,
) {
    infoRoutes(server, _bibleCatalog, _catalog, _fileUploadEnabled, _maxMediaUploadMb)
    songRoutes(server, _catalog, json, scope)
}

private fun Route.infoRoutes(
    server: CompanionServer,
    _bibleCatalog: MutableStateFlow<BibleCatalogResponse?>,
    _catalog: MutableStateFlow<SongCatalogResponse>,
    _fileUploadEnabled: MutableStateFlow<Boolean>,
    _maxMediaUploadMb: MutableStateFlow<Int>,
) {
                get(Constants.ENDPOINT_INFO) {
                    if (!server.checkApiKey(call)) return@get
                    call.respond(ServerInfoResponse(port = server.currentPort))
                }

                get(Constants.ENDPOINT_STATUS) {
                    if (!server.checkApiKey(call)) return@get
                    val bibleNames = _bibleCatalog.value?.translation?.let { listOf(it) } ?: emptyList()
                    val songbookNames = _catalog.value.songBook.map { it.bookName }
                    val exposedEndpoints = listOf(
                        "songs", "bible", "schedule", "presentations", "pictures", "status"
                    )
                    call.response.headers.append(Constants.HEADER_SERVER_VERSION, server.host.appVersion)
                    call.respond(
                        StatusResponse(
                            appVersion  = server.host.appVersion,
                            endpoints   = exposedEndpoints,
                            bibles      = bibleNames,
                            songbooks   = songbookNames,
                            permissions = DevicePermissionsDto(
                                canPresent       = true,
                                canAddToSchedule = true,
                                canUploadFiles   = _fileUploadEnabled.value,
                                maxMediaUploadMb = _maxMediaUploadMb.value,
                            ),
                        )
                    )
                }
}

private fun Route.songRoutes(
    server: CompanionServer,
    _catalog: MutableStateFlow<SongCatalogResponse>,
    json: Json,
    scope: CoroutineScope,
) {
                get(Constants.ENDPOINT_SONGS) {
                    if (!server.checkApiKey(call)) return@get
                    val filter = call.request.queryParameters[Constants.QUERY_PARAM_SONGBOOK]
                    val catalog = _catalog.value
                    if (filter.isNullOrBlank()) {
                        call.respond(catalog)
                    } else {
                        val filtered = catalog.songBook.filter { it.bookName == filter }
                        call.respond(SongCatalogResponse(
                            songBook = filtered,
                            songBooks = filtered.size,
                            total = filtered.sumOf { it.songTotal }
                        ))
                    }
                }

                /**
                 * GET /api/songs/{number}[?songbook=Name]
                 * Returns full song details including all lyric sections.
                 * Use ?songbook= to disambiguate when the same number exists in multiple songbooks.
                 */
                get("${Constants.ENDPOINT_SONGS}/{identifier}") {
                    if (!server.checkApiKey(call)) return@get
                    val identifier = call.parameters["identifier"] ?: run {
                        server.logRest("/api/songs/{identifier}", HttpStatusCode.BadRequest.value, "missing_identifier")
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing identifier"}""")
                        return@get
                    }
                    val songbookFilter = call.request.queryParameters[Constants.QUERY_PARAM_SONGBOOK]
                    val titleFilter = call.request.queryParameters["title"]
                    // Try index-based lookup first (id=N query param)
                    val idParam = call.request.queryParameters["id"]?.toIntOrNull()
                    val song = if (idParam != null) {
                        server._songs.getOrNull(idParam)
                    } else {
                        // Fall back to number + songbook match; treat "_" as empty number
                        val lookupNumber = if (identifier == "_") "" else identifier
                        server._songs.firstOrNull { s ->
                            val matchesSongbook = songbookFilter.isNullOrBlank() || s.songbook.equals(
                                songbookFilter,
                                ignoreCase = true,
                            )
                            val matchesNumber = s.number == lookupNumber
                            val matchesTitle = !titleFilter.isNullOrBlank() && s.title.equals(
                                titleFilter,
                                ignoreCase = true,
                            )
                            matchesSongbook && (matchesNumber || matchesTitle)
                        }
                    }
                    if (song == null) {
                        server.logRest("/api/songs/{identifier}", HttpStatusCode.NotFound.value, "song_not_found")
                        call.respond(HttpStatusCode.NotFound, """{"error":"song not found"}""")
                        return@get
                    }
                    server.logRest("/api/songs/{identifier}", HttpStatusCode.OK.value)
                    call.respond(buildSongDetail(song))
                }

                /**
                 * POST /api/songs/{number}/select
                 * Body: { "section": 2 }   — OR —   ?section=2 as query param
                 *
                 * Navigates the live presenter to section [section] (0-based) of the currently
                 * projected song.  No approval required — fires instantly.
                 *
                 * Response: {"ok":true}
                 */
    songSelectRoutes(server, json, scope)
}

private fun Route.songSelectRoutes(
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
) {
                post("${Constants.ENDPOINT_SONGS}/{number}/select") {
                    if (!server.allowsRequest(call)) return@post
                    val number = call.parameters["number"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing number"}""")
                        return@post
                    }
                    // Accept section from query param OR JSON body
                    val sectionIndex = call.request.queryParameters["section"]?.toIntOrNull()
                        ?: runCatching {
                            json.decodeFromString(SelectSongSectionRequest.serializer(), call.receiveText()).section
                        }.getOrNull()
                    if (sectionIndex == null || sectionIndex < 0) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing or invalid section index"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val allowed = server.requestApproval(
                        "present", "Song $number", "Section $sectionIndex", clientId,
                    )
                    if (!allowed) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@post
                    }
                    scope.launch { server.onSelectSongSection.emit(SelectSongSectionRequest(number, sectionIndex)) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }
}


