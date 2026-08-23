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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.bible.Bible
import org.churchpresenter.dictionary.DictionaryVerseDto
import org.churchpresenter.dictionary.DictionaryVersesResponse
import org.churchpresenter.dictionary.StrongsDictionaryRepository
import org.churchpresenter.dictionary.StrongsEntryDto
import org.churchpresenter.settings.utils.Constants

private const val SUMMARY_PREVIEW_CHARS = 60

/**
 * Routes for the Bible catalogue, chapter/verse lookup and the Strong's dictionary.
 *
 * Body moved verbatim from `CompanionServer` — its raw-string literals make the indentation
 * load-bearing. Read-only access to the loaded Bible arrives as identically-named parameters, so
 * the state itself stays private to the server.
 */
internal fun Route.bibleAndDictionaryRoutes(
    server: CompanionServer,
    _bible: MutableStateFlow<Bible?>,
    _bibleCatalog: MutableStateFlow<BibleCatalogResponse?>,
    json: Json,
    scope: CoroutineScope,
) {
    bibleReadRoutes(server, _bible, _bibleCatalog)
    dictionaryRoutes(server, _bible, json)
    bibleSelectRoutes(server, json, scope)
}

private fun Route.bibleReadRoutes(
    server: CompanionServer,
    _bible: MutableStateFlow<Bible?>,
    _bibleCatalog: MutableStateFlow<BibleCatalogResponse?>,
) {
                get(Constants.ENDPOINT_BIBLE) {
                    if (!server.checkApiKey(call)) return@get
                    val catalog = _bibleCatalog.value
                    if (catalog == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, "Bible not loaded")
                        return@get
                    }
                    val bookParam     = call.request.queryParameters[Constants.QUERY_PARAM_BOOK]
                    val chapterFilter = call.request.queryParameters[Constants.QUERY_PARAM_CHAPTER]?.toIntOrNull()

                    // ── Numeric book id + chapter → return full chapter with verse text ──
                    val bookIdParam = bookParam?.toIntOrNull()
                    if (bookIdParam != null && chapterFilter != null) {
                        val bible = _bible.value
                        if (bible == null) {
                            call.respond(HttpStatusCode.ServiceUnavailable, "Bible not loaded")
                            return@get
                        }
                        val rawVerses = bible.getChapterVerses(bookIdParam, chapterFilter)
                        if (rawVerses.isEmpty()) {
                            call.respond(HttpStatusCode.NotFound, "Chapter not found")
                            return@get
                        }
                        val bookName = bible.getBookName(bookIdParam) ?: "Book $bookIdParam"
                        val verseDtos = rawVerses.map { BibleVerseDto(verse = it.verseNumber, text = it.verseText) }
                        call.respond(BibleChapterResponse(
                            translation = catalog.translation,
                            bookId = bookIdParam,
                            bookName = bookName,
                            chapter = chapterFilter,
                            verseTotal = verseDtos.size,
                            verses = verseDtos
                        ))
                        return@get
                    }

                    if (bookParam.isNullOrBlank()) {
                        call.respond(catalog)
                    } else {
                        val filteredBooks = catalog.books.filter {
                            it.bookName.equals(bookParam, ignoreCase = true)
                        }.map { book ->
                            if (chapterFilter != null) {
                                book.copy(chapters = book.chapters.filter { it.chapter == chapterFilter })
                            } else book
                        }
                        call.respond(catalog.copy(
                            books = filteredBooks,
                            bookTotal = filteredBooks.size,
                            verseTotal = filteredBooks.sumOf { b -> b.chapters.sumOf { it.verseTotal } }
                        ))
                    }
                }

                // ── Strong's dictionary endpoints ─────────────────────────────

                /**
                 * GET /api/dictionary?q=&lang=en|ru&filter=all|hebrew|greek&limit=100
                 *        [&book=1[&chapter=1[&verse=1]]]
                 * Returns a JSON array of matching [StrongsEntry] objects.
                 *
                 * The optional book/chapter/verse params (canonical KJV numbering,
                 * Genesis=1 … Revelation=66 — same as /api/bible book-id) restrict
                 * results to the Strong's numbers occurring in that reference,
                 * narrowing progressively as chapter and verse are added.
                 */
}

private fun Route.dictionaryRoutes(
    server: CompanionServer,
    _bible: MutableStateFlow<Bible?>,
    json: Json,
) {
                get(Constants.ENDPOINT_DICTIONARY) {
                    if (!server.checkApiKey(call)) return@get
                    val q       = call.request.queryParameters["q"] ?: ""
                    val lang    = call.request.queryParameters["lang"]
                    val filter  = call.request.queryParameters["filter"] ?: "all"
                    val limit   = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val book    = call.request.queryParameters["book"]?.toIntOrNull()
                    val chapter = call.request.queryParameters["chapter"]?.toIntOrNull()
                    val verse   = call.request.queryParameters["verse"]?.toIntOrNull()
                    val results = try {
                        StrongsDictionaryRepository.shared.search(q, lang, filter, limit, book, chapter, verse)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.ServiceUnavailable, """{"error":"dictionary unavailable"}""")
                        return@get
                    }
                    call.respondText(
                        json.encodeToString(ListSerializer(StrongsEntryDto.serializer()), results),
                        ContentType.Application.Json
                    )
                }

                /**
                 * GET /api/dictionary/{number}?lang=en|ru
                 * Returns a single [StrongsEntry] (e.g. /api/dictionary/H430), or 404.
                 */
                get(Constants.ENDPOINT_DICTIONARY_ENTRY) {
                    if (!server.checkApiKey(call)) return@get
                    val number = call.parameters["number"]
                    if (number.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing number"}""")
                        return@get
                    }
                    val lang = call.request.queryParameters["lang"]
                    val entry = try {
                        StrongsDictionaryRepository.shared.lookup(number, lang)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.ServiceUnavailable, """{"error":"dictionary unavailable"}""")
                        return@get
                    }
                    if (entry == null) {
                        call.respond(HttpStatusCode.NotFound, """{"error":"entry not found"}""")
                        return@get
                    }
                    call.respondText(
                        json.encodeToString(StrongsEntryDto.serializer(), entry),
                        ContentType.Application.Json
                    )
                }

                /**
                 * GET /api/dictionary/{number}/verses?limit=25[&book=1[&chapter=1[&verse=1]]]
                 * Returns the verses (reference + translation text) in which the
                 * Strong's number appears, for the entry sheet's "Appears in" list.
                 *
                 * Optional book/chapter/verse (canonical KJV numbering) order the
                 * references in that scope first, so the verse being filtered leads.
                 */
                get(Constants.ENDPOINT_DICTIONARY_VERSES) {
                    if (!server.checkApiKey(call)) return@get
                    val number = call.parameters["number"]
                    if (number.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing number"}""")
                        return@get
                    }
                    val limit   = call.request.queryParameters["limit"]?.toIntOrNull() ?: 25
                    val book    = call.request.queryParameters["book"]?.toIntOrNull()
                    val chapter = call.request.queryParameters["chapter"]?.toIntOrNull()
                    val verse   = call.request.queryParameters["verse"]?.toIntOrNull()
                    val bible = _bible.value
                    if (bible == null) {
                        call.respond(HttpStatusCode.ServiceUnavailable, """{"error":"bible not loaded"}""")
                        return@get
                    }
                    val (total, refs) = try {
                        StrongsDictionaryRepository.shared.versesFor(number, limit, book, chapter, verse)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.ServiceUnavailable, """{"error":"dictionary unavailable"}""")
                        return@get
                    }
                    val verses = refs.map { ref ->
                        val bId = ref.substring(0, 3).toInt()
                        val ch  = ref.substring(3, 6).toInt()
                        val vs  = ref.substring(6, 9).toInt()
                        val bookName = bible.getBookName(bId) ?: "Book $bId"
                        val text = bible.getChapterVerses(bId, ch)
                            .firstOrNull { it.verseNumber == vs }?.verseText ?: ""
                        DictionaryVerseDto(
                            bookName = bookName, chapter = ch, verse = vs,
                            reference = "$bookName $ch:$vs", text = text
                        )
                    }
                    call.respondText(
                        json.encodeToString(
                            DictionaryVersesResponse.serializer(),
                            DictionaryVersesResponse(number = number, total = total, verses = verses)
                        ),
                        ContentType.Application.Json
                    )
                }

                // ── Presentation endpoints ────────────────────────────────────

                /**
                 * POST /api/bible/select
                 * Body: { "bookName": "John", "chapter": 3, "verseNumber": 16,
                 *         "verseText": "For God so loved…", "verseRange": "" }
                 *
                 * Instantly displays the given verse on the presentation output.
                 * No approval dialog — fires immediately like select_picture / select_song_section.
                 * Response: {"ok":true}
                 */
}

private fun Route.bibleSelectRoutes(
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
) {
                post(Constants.ENDPOINT_BIBLE_SELECT) {
                    if (!server.allowsRequest(call)) return@post
                    val body = call.receiveText()
                    val req = try {
                        json.decodeFromString(SelectBibleVerseRequest.serializer(), body)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"invalid request body"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val verseRef = if (req.verseRange.isNotEmpty()) "${req.bookName} ${req.chapter}:${req.verseRange}"
                                   else "${req.bookName} ${req.chapter}:${req.verseNumber}"
                    val allowed = server.requestApproval(
                        "present", verseRef, req.verseText.take(SUMMARY_PREVIEW_CHARS), clientId,
                    )
                    if (!allowed) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@post
                    }
                    scope.launch { server.onSelectBibleVerse.emit(req) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                // ── Presentation endpoints ────────────────────────────────────

                /**
                 * GET /api/presentations
                 *
                 * Returns only the presentation that is currently loaded in the desktop
                 * Presentations tab ([_presentationCatalog]).  The mobile list should
                 * mirror what the desktop shows — not accumulate every file that has ever
                 * been opened.  Schedule-driven navigation uses
                 * GET /api/presentations/{id} directly via [navigateTo], so individual
                 * schedule items are still accessible without polluting this list.
                 */
}

