package org.churchpresenter.companionserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.core.models.qa.QuestionDto
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.core.models.qa.SubmitQuestionRequest
import org.churchpresenter.core.models.qa.VoteRequest
import org.churchpresenter.core.models.qa.toDto
import org.churchpresenter.settings.utils.Constants

/**
 * Routes for the Q&A submission, voting and moderation endpoints.
 *
 * Extracted from `CompanionServer` verbatim — the body's indentation is left as it was, because
 * it contains raw-string HTML/JSON literals whose emitted content depends on it.
 *
 * [json] and [scope] are passed in rather than reached for: the only call site is inside
 * `CompanionServer.configurePipeline`, which can read its own privates, so neither has to be
 * widened. [server] is needed for the Q&A state that is mutable and read per request
 * (`qaManager`, `qaCooldownSeconds`, `qaVotingEnabled`) — capturing those by value here would
 * freeze them at wiring time.
 */
internal fun Route.qaRoutes(
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
) {
    qaPublicRoutes(server, json)
    qaModerationRoutes(server, json, scope)
    qaAdminActionRoutes(server, json, scope)
}

private fun Route.qaPublicRoutes(
    server: CompanionServer,
    json: Json,
) {
                get("/qa") {
                    call.respondText(qaSubmissionPageHtml(), ContentType.Text.Html)
                }

                // Public: admin page
                get("/qa/admin") {
                    call.respondText(qaAdminPageHtml(), ContentType.Text.Html)
                }

                // Public: session status
                get("/api/qa/status") {
                    val qa: QaStore? = server.qaStore
                    call.respondText(
                        """{"sessionActive":${qa?.sessionActive ?: false},""" +
                            """"cooldownSeconds":${server.qaCooldownSeconds},""" +
                            """"displayedQuestionId":"${qa?.displayedQuestion?.id ?: ""}",""" +
                            """"votingEnabled":${server.qaVotingEnabled}}""",
                        ContentType.Application.Json
                    )
                }

                // Public: submit a question (no API key)
                post("/api/qa/submit") {
                    val qa: QaStore? = server.qaStore
                    if (qa == null || !qa.sessionActive) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"Q&A session is not active"}""")
                        return@post
                    }
                    val body = call.receiveText()
                    val request = try {
                        json.decodeFromString(SubmitQuestionRequest.serializer(), body)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"invalid request"}""")
                        return@post
                    }
                    if (request.text.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"question text is required"}""")
                        return@post
                    }
                    val clientIp = call.request.headers["CF-Connecting-IP"]
                        ?: call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                        ?: call.request.local.remoteAddress
                    val deviceId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val question = qa.submitQuestion(
                        request.text,
                        request.name,
                        clientIp,
                        server.qaCooldownSeconds,
                        deviceId,
                    )
                    if (question != null) {
                        call.respondText(
                            json.encodeToString(QuestionDto.serializer(), question.toDto()),
                            ContentType.Application.Json
                        )
                    } else {
                        if (qa.isRateLimited(clientIp, server.qaCooldownSeconds)) {
                            call.respond(
                                HttpStatusCode.TooManyRequests,
                                """{"error":"Too many questions. Please wait a moment."}""",
                            )
                        } else {
                            call.respond(HttpStatusCode.Forbidden, """{"error":"submission failed"}""")
                        }
                    }
                }

                // Public: voting page
    qaVotingRoutes(server, json)
}

private fun Route.qaVotingRoutes(
    server: CompanionServer,
    json: Json,
) {
                get("/qa/vote") {
                    call.respondText(qaVotingPageHtml(), ContentType.Text.Html)
                }

                // Public: list approved questions (for voting)
                get("/api/qa/approved") {
                    if (!server.qaVotingEnabled) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"Voting is not enabled"}""")
                        return@get
                    }
                    val qa: QaStore? = server.qaStore
                    if (qa == null || !qa.sessionActive) {
                        call.respondText("[]", ContentType.Application.Json)
                        return@get
                    }
                    val approved = qa.getApprovedQuestions()
                    val clientIp = call.request.headers["CF-Connecting-IP"]
                        ?: call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                        ?: call.request.local.remoteAddress
                    val dtos = approved.map {
                        val dto = it.toDto()
                        val textEsc = dto.text
                        .replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n").replace("\r", "\\r")
                        val voteDir = qa.getVoteDirection(it.id, clientIp)
                        val votedStr = if (voteDir != null) "\"$voteDir\"" else "null"
                        """{"id":"${dto.id}","text":"$textEsc","voteCount":${dto.voteCount},"voted":$votedStr}"""
                    }
                    call.respondText("[${dtos.joinToString(",")}]", ContentType.Application.Json)
                }

                // Public: vote for a question
                post("/api/qa/vote") {
                    if (!server.qaVotingEnabled) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"Voting is not enabled"}""")
                        return@post
                    }
                    val qa: QaStore? = server.qaStore
                    if (qa == null || !qa.sessionActive) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"Q&A session is not active"}""")
                        return@post
                    }
                    val body = call.receiveText()
                    val request = try {
                        json.decodeFromString(VoteRequest.serializer(), body)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"invalid request"}""")
                        return@post
                    }
                    val question = qa.findQuestion(request.questionId)
                    if (question == null) {
                        call.respond(HttpStatusCode.NotFound, """{"error":"question not found"}""")
                        return@post
                    }
                    if (question.status != QuestionStatus.APPROVED) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"question is not available for voting"}""")
                        return@post
                    }
                    val clientIp = call.request.headers["CF-Connecting-IP"]
                        ?: call.request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
                        ?: call.request.local.remoteAddress
                    val direction = if (request.direction == "down") "down" else "up"
                    qa.voteForQuestion(request.questionId, clientIp, direction)
                    val currentDir = qa.getVoteDirection(request.questionId, clientIp)
                    val voted = if (currentDir != null) "\"$currentDir\"" else "null"
                    call.respondText("""{"ok":true,"voted":$voted}""", ContentType.Application.Json)
                }

                // Admin: check password
}


private fun Route.qaModerationRoutes(
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
) {
                post("/api/qa/auth") {
                    if (!server.checkQaAdmin(call)) return@post
                    if (!server.checkQaAdminConnect(call)) return@post
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                // Admin: list questions
                get("/api/qa/questions") {
                    if (!server.checkQaAdmin(call)) return@get
                    val qa = server.qaStore ?: run {
                        call.respondText("[]", ContentType.Application.Json)
                        return@get
                    }
                    val statusFilter = call.request.queryParameters["status"]
                    val filtered = if (statusFilter != null) {
                        val s = try { QuestionStatus.valueOf(statusFilter.uppercase()) } catch (_: Exception) { null }
                        if (s != null) qa.questions.filter { it.status == s } else qa.questions
                    } else qa.questions
                    val dtos = filtered.map { it.toDto() }
                    call.respondText(
                        json.encodeToString(ListSerializer(QuestionDto.serializer()), dtos),
                        ContentType.Application.Json
                    )
                }

                // Admin: approve question
                post("/api/qa/questions/{id}/approve") {
                    if (!server.checkQaAdmin(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val question = server.qaStore?.findQuestion(id)
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = CompanionServer.PendingQAAdminRequest(
                        action = "approve",
                        questionId = id,
                        text = question?.text ?: "",
                        clientId = clientId,
                    )
                    server.onQAAdminRequest.emit(pending)
                    if (!pending.decision.await()) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@post
                    }
                    val ok = server.qaStore?.approveQuestion(id) ?: false
                    if (ok) call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    else call.respond(HttpStatusCode.NotFound, """{"error":"question not found"}""")
                }

                // Admin: edit question text
    qaQuestionEditRoutes(server, json, scope)
    qaQuestionStateRoutes(server, scope)
}

private fun Route.qaQuestionEditRoutes(
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
) {
                post("/api/qa/questions/{id}/edit") {
                    if (!server.checkQaAdmin(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val body = call.receiveText()
                    val request = try {
                        json.decodeFromString(SubmitQuestionRequest.serializer(), body)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"invalid request"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = CompanionServer.PendingQAAdminRequest(
                        action = "edit",
                        questionId = id,
                        text = request.text,
                        clientId = clientId
                    )
                    server.onQAAdminRequest.emit(pending)
                    val allowed = pending.decision.await()
                    if (!allowed) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@post
                    }
                    val ok = server.qaStore?.editQuestion(id, request.text) ?: false
                    if (ok) call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    else call.respond(HttpStatusCode.NotFound, """{"error":"question not found"}""")
                }

                // Admin: deny question
                post("/api/qa/questions/{id}/deny") {
                    if (!server.checkQaAdmin(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val question = server.qaStore?.findQuestion(id)
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = CompanionServer.PendingQAAdminRequest(
                        action = "deny",
                        questionId = id,
                        text = question?.text ?: "",
                        clientId = clientId,
                    )
                    server.onQAAdminRequest.emit(pending)
                    if (!pending.decision.await()) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@post
                    }
                    val ok = server.qaStore?.denyQuestion(id) ?: false
                    if (ok) {
                        if (server.qaStore?.displayedQuestion == null) scope.launch { server.onQADisplay.emit(null) }
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    }
                    else call.respond(HttpStatusCode.NotFound, """{"error":"question not found"}""")
                }

                // Admin: mark question as done
}

private fun Route.qaQuestionStateRoutes(
    server: CompanionServer,
    scope: CoroutineScope,
) {
                post("/api/qa/questions/{id}/done") {
                    if (!server.checkQaAdmin(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val question = server.qaStore?.findQuestion(id)
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = CompanionServer.PendingQAAdminRequest(
                        action = "done",
                        questionId = id,
                        text = question?.text ?: "",
                        clientId = clientId,
                    )
                    server.onQAAdminRequest.emit(pending)
                    if (!pending.decision.await()) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@post
                    }
                    val ok = server.qaStore?.markDone(id) ?: false
                    if (ok) {
                        if (server.qaStore?.displayedQuestion == null) scope.launch { server.onQADisplay.emit(null) }
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    }
                    else call.respond(HttpStatusCode.NotFound, """{"error":"question not found"}""")
                }

                // Admin: display question on projection
                post("/api/qa/questions/{id}/display") {
                    if (!server.checkQaAdmin(call)) return@post
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@post
                    }
                    val question = server.qaStore?.findQuestion(id)
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = CompanionServer.PendingQAAdminRequest(
                        action = "display",
                        questionId = id,
                        text = question?.text ?: "",
                        clientId = clientId,
                    )
                    server.onQAAdminRequest.emit(pending)
                    if (!pending.decision.await()) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@post
                    }
                    val qa = server.qaStore
                    val ok = qa?.displayQuestion(id) ?: false
                    if (ok) {
                        scope.launch { server.onQADisplay.emit(qa.displayedQuestion) }
                        call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    }
                    else call.respond(HttpStatusCode.NotFound, """{"error":"question not found or not approved"}""")
                }

                // Admin: delete question
    qaQuestionDeleteRoutes(server)
}

private fun Route.qaQuestionDeleteRoutes(
    server: CompanionServer,
) {
                delete("/api/qa/questions/{id}") {
                    if (!server.checkQaAdmin(call)) return@delete
                    val id = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"missing id"}""")
                        return@delete
                    }
                    val question = server.qaStore?.findQuestion(id)
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = CompanionServer.PendingQAAdminRequest(
                        action = "delete",
                        questionId = id,
                        text = question?.text ?: "",
                        clientId = clientId
                    )
                    server.onQAAdminRequest.emit(pending)
                    val allowed = pending.decision.await()
                    if (!allowed) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@delete
                    }
                    val ok = server.qaStore?.deleteQuestion(id) ?: false
                    if (ok) call.respondText("""{"ok":true}""", ContentType.Application.Json)
                    else call.respond(HttpStatusCode.NotFound, """{"error":"question not found"}""")
                }

                // Admin: add question (admin-created)
}



private fun Route.qaAdminActionRoutes(
    server: CompanionServer,
    json: Json,
    scope: CoroutineScope,
) {
                post("/api/qa/add") {
                    if (!server.checkQaAdmin(call)) return@post
                    val body = call.receiveText()
                    val request = try {
                        json.decodeFromString(SubmitQuestionRequest.serializer(), body)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"invalid request"}""")
                        return@post
                    }
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = CompanionServer.PendingQAAdminRequest(
                        action = "add",
                        text = request.text,
                        clientId = clientId
                    )
                    server.onQAAdminRequest.emit(pending)
                    val allowed = pending.decision.await()
                    if (!allowed) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"denied by operator"}""")
                        return@post
                    }
                    val question = server.qaStore?.addQuestion(request.text)
                    if (question != null) {
                        call.respondText(
                            json.encodeToString(QuestionDto.serializer(), question.toDto()),
                            ContentType.Application.Json
                        )
                    } else {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"failed to add question"}""")
                    }
                }

                // Admin: clear display
                post("/api/qa/clear-display") {
                    if (!server.checkQaAdmin(call)) return@post
                    val clientId = call.request.headers[Constants.HEADER_DEVICE_ID] ?: ""
                    val pending = CompanionServer.PendingQAAdminRequest(action = "clear-display", clientId = clientId)
                    server.onQAAdminRequest.emit(pending)
                    if (!pending.decision.await()) {
                        call.respond(HttpStatusCode.Forbidden, """{"error":"denied"}""")
                        return@post
                    }
                    server.qaStore?.clearDisplay()
                    scope.launch { server.onQADisplay.emit(null) }
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }

                // Admin: clear all questions
                post("/api/qa/clear-all") {
                    if (!server.checkQaAdmin(call)) return@post
                    server.qaStore?.clearAll()
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }
}

