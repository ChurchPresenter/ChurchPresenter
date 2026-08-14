package org.churchpresenter.app.churchpresenter.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.net.URLEncoder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Everything [PlanningCenterClient] does over the network besides attachment downloads (covered
 * separately by [PlanningCenterDownloadTest]): the OAuth token exchange, `/people/v2/me`, and the
 * Services API for service types, plans, plan items, arrangement lyrics and attachment metadata.
 *
 * Every request here is served by a `MockEngine` passed to the call under test, so nothing in this
 * class touches the real Planning Center API. The client is an ordinary instance field: JUnit builds
 * a new instance of this class per test method, so there is nothing global to reset.
 */
class PlanningCenterClientNetworkTest {

    private val requests = mutableListOf<HttpRequestData>()
    private lateinit var http: HttpClient

    private fun respondWith(body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        http = HttpClient(
            MockEngine { request ->
                requests.add(request)
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
    }

    private fun failToConnect() {
        http = HttpClient(
            MockEngine { throw java.io.IOException("no route to host") },
        )
    }

    // ── Token exchange ──────────────────────────────────────────────────────────

    @Test
    fun `an authorization code is exchanged for a token set`() {
        respondWith("""{"access_token":"tok-abc","refresh_token":"ref-abc","expires_in":7200}""")

        val outcome =
            runBlocking { PlanningCenterClient.exchangeCodeForToken("cid", "csecret", "the-code", http = http) }

        val tokens = assertIs<PlanningCenterClient.TokenOutcome.Success>(outcome, "got $outcome").tokens
        assertEquals("tok-abc", tokens.accessToken)
        assertEquals("ref-abc", tokens.refreshToken)
        assertTrue(
            tokens.expiresAtEpochMs > System.currentTimeMillis(),
            "a 2-hour token has to expire in the future",
        )
    }

    @Test
    fun `the code exchange authenticates with http basic and sends the redirect uri`() {
        respondWith("""{"access_token":"t","refresh_token":"r","expires_in":100}""")

        runBlocking { PlanningCenterClient.exchangeCodeForToken("cid", "csecret", "the-code", http = http) }

        val request = requests.single()
        val expectedBasic = "Basic " + Base64.getEncoder().encodeToString("cid:csecret".toByteArray())
        assertEquals(expectedBasic, request.headers[HttpHeaders.Authorization])
        val body = String((request.body as OutgoingContent.ByteArrayContent).bytes())
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("code=the-code"))
        assertTrue(body.contains("client_id=cid"))
        assertTrue(body.contains("client_secret=csecret"))
        assertTrue(body.contains(URLEncoder.encode(PlanningCenterClient.redirectUri(), "UTF-8")))
    }

    @Test
    fun `a refresh token is exchanged with the refresh grant instead of a code`() {
        respondWith("""{"access_token":"t2","refresh_token":"r2","expires_in":100}""")

        runBlocking { PlanningCenterClient.refreshAccessToken("cid", "csecret", "old-refresh", http = http) }

        val body = String((requests.single().body as OutgoingContent.ByteArrayContent).bytes())
        assertTrue(body.contains("grant_type=refresh_token"))
        assertTrue(body.contains("refresh_token=old-refresh"))
        assertTrue(!body.contains("code=the-code"), "a refresh must not also claim to be a code exchange")
    }

    @Test
    fun `a rejected client id or secret is told apart from a generic failure`() {
        respondWith("""{"error":"invalid_client"}""", HttpStatusCode.Unauthorized)
        assertEquals(
            PlanningCenterClient.TokenOutcome.InvalidCredentials,
            runBlocking { PlanningCenterClient.exchangeCodeForToken("bad", "bad", "code", http = http) },
        )

        respondWith("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
        assertEquals(
            PlanningCenterClient.TokenOutcome.InvalidCredentials,
            runBlocking { PlanningCenterClient.exchangeCodeForToken("cid", "csecret", "stale-code", http = http) },
            "a reused or expired code is the same operator-facing problem as a bad credential",
        )
    }

    @Test
    fun `a blank access token in an otherwise successful response is treated as invalid credentials`() {
        respondWith("""{"access_token":"","refresh_token":"r","expires_in":100}""")

        assertEquals(
            PlanningCenterClient.TokenOutcome.InvalidCredentials,
            runBlocking { PlanningCenterClient.exchangeCodeForToken("cid", "csecret", "code", http = http) },
        )
    }

    @Test
    fun `a server error on the token endpoint is a plain failure`() {
        respondWith("""{"error":"boom"}""", HttpStatusCode.InternalServerError)

        assertEquals(
            PlanningCenterClient.TokenOutcome.Failure,
            runBlocking { PlanningCenterClient.exchangeCodeForToken("cid", "csecret", "code", http = http) },
        )
    }

    @Test
    fun `an unreachable token endpoint is a network error`() {
        failToConnect()

        assertEquals(
            PlanningCenterClient.TokenOutcome.NetworkError,
            runBlocking { PlanningCenterClient.exchangeCodeForToken("cid", "csecret", "code", http = http) },
        )
    }

    // ── The connected person ────────────────────────────────────────────────────

    @Test
    fun `the connected person's name comes from the name attribute when present`() {
        respondWith("""{"data":{"id":"1","attributes":{"name":"Pat Ringer","first_name":"Pat","last_name":"Ringer"}}}""")

        val outcome = runBlocking { PlanningCenterClient.getCurrentPerson("tok", http = http) }

        assertEquals("Pat Ringer", assertIs<PlanningCenterClient.PersonOutcome.Success>(outcome).person.displayName)
    }

    @Test
    fun `the connected person falls back to first and last name when name is absent`() {
        respondWith("""{"data":{"id":"1","attributes":{"first_name":"Pat","last_name":"Ringer"}}}""")

        val outcome = runBlocking { PlanningCenterClient.getCurrentPerson("tok", http = http) }

        assertEquals("Pat Ringer", assertIs<PlanningCenterClient.PersonOutcome.Success>(outcome).person.displayName)
    }

    @Test
    fun `the connected person falls back to a generic label when no name field is present at all`() {
        respondWith("""{"data":{"id":"1","attributes":{}}}""")

        val outcome = runBlocking { PlanningCenterClient.getCurrentPerson("tok", http = http) }

        assertEquals(
            "Connected",
            assertIs<PlanningCenterClient.PersonOutcome.Success>(outcome).person.displayName,
            "settings still shows something rather than a blank row",
        )
    }

    @Test
    fun `the access token travels as a bearer header`() {
        respondWith("""{"data":{"id":"1","attributes":{"name":"Pat"}}}""")

        runBlocking { PlanningCenterClient.getCurrentPerson("secret-token", http = http) }

        assertEquals("Bearer secret-token", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `an expired token asking who is connected is unauthorized, not a failure`() {
        respondWith("""{}""", HttpStatusCode.Unauthorized)
        assertEquals(
            PlanningCenterClient.PersonOutcome.Unauthorized,
            runBlocking { PlanningCenterClient.getCurrentPerson("tok", http = http) },
        )

        respondWith("""{}""", HttpStatusCode.Forbidden)
        assertEquals(
            PlanningCenterClient.PersonOutcome.Unauthorized,
            runBlocking { PlanningCenterClient.getCurrentPerson("tok", http = http) },
        )
    }

    @Test
    fun `a server error asking who is connected is a plain failure`() {
        respondWith("""{}""", HttpStatusCode.InternalServerError)
        assertEquals(
            PlanningCenterClient.PersonOutcome.Failure,
            runBlocking { PlanningCenterClient.getCurrentPerson("tok", http = http) },
        )
    }

    @Test
    fun `an unreachable people endpoint is a network error`() {
        failToConnect()
        assertEquals(
            PlanningCenterClient.PersonOutcome.NetworkError,
            runBlocking { PlanningCenterClient.getCurrentPerson("tok", http = http) },
        )
    }

    // ── Service types ───────────────────────────────────────────────────────────

    @Test
    fun `service types are read from the data array`() {
        respondWith(
            """{"data":[
                {"id":"1","attributes":{"name":"Sunday Service"}},
                {"id":"2","attributes":{"name":"Youth Service"}}]}""",
        )

        val outcome = runBlocking { PlanningCenterClient.listServiceTypes("tok", http = http) }

        assertEquals(
            listOf(
                PlanningCenterClient.ServiceType("1", "Sunday Service"),
                PlanningCenterClient.ServiceType("2", "Youth Service"),
            ),
            assertIs<PlanningCenterClient.ServiceTypesOutcome.Success>(outcome).serviceTypes,
        )
    }

    @Test
    fun `no service types is a success with an empty list`() {
        respondWith("""{"data":[]}""")

        assertTrue(
            assertIs<PlanningCenterClient.ServiceTypesOutcome.Success>(
                runBlocking { PlanningCenterClient.listServiceTypes("tok", http = http) },
            ).serviceTypes.isEmpty(),
        )
    }

    @Test
    fun `an unauthorized service types request is told apart from a failure`() {
        respondWith("""{}""", HttpStatusCode.Unauthorized)
        assertEquals(
            PlanningCenterClient.ServiceTypesOutcome.Unauthorized,
            runBlocking { PlanningCenterClient.listServiceTypes("tok", http = http) },
        )
    }

    @Test
    fun `a server error listing service types is a plain failure`() {
        respondWith("""{}""", HttpStatusCode.InternalServerError)
        assertEquals(
            PlanningCenterClient.ServiceTypesOutcome.Failure,
            runBlocking { PlanningCenterClient.listServiceTypes("tok", http = http) },
        )
    }

    @Test
    fun `an unreachable services endpoint is a network error listing service types`() {
        failToConnect()
        assertEquals(
            PlanningCenterClient.ServiceTypesOutcome.NetworkError,
            runBlocking { PlanningCenterClient.listServiceTypes("tok", http = http) },
        )
    }

    // ── Upcoming plans ──────────────────────────────────────────────────────────

    @Test
    fun `a plan's title comes from the title attribute when present`() {
        respondWith("""{"data":[{"id":"9","attributes":{"title":"Easter Sunday","dates":"April 12, 2026","series_title":"Easter"}}]}""")

        val plans = assertIs<PlanningCenterClient.PlansOutcome.Success>(
            runBlocking { PlanningCenterClient.listUpcomingPlans("tok", "svc-1", http = http) },
        ).plans

        assertEquals(PlanningCenterClient.Plan("9", "Easter Sunday", "April 12, 2026"), plans.single())
    }

    @Test
    fun `a plan with no title falls back to the series title`() {
        respondWith("""{"data":[{"id":"9","attributes":{"title":"","series_title":"Easter","dates":""}}]}""")

        val plan = assertIs<PlanningCenterClient.PlansOutcome.Success>(
            runBlocking { PlanningCenterClient.listUpcomingPlans("tok", "svc-1", http = http) },
        ).plans.single()

        assertEquals("Easter", plan.title, "a blank per-plan title is common; the series still names it")
    }

    @Test
    fun `a plan with no title or series title falls back to a generic label`() {
        respondWith("""{"data":[{"id":"9","attributes":{}}]}""")

        val plan = assertIs<PlanningCenterClient.PlansOutcome.Success>(
            runBlocking { PlanningCenterClient.listUpcomingPlans("tok", "svc-1", http = http) },
        ).plans.single()

        assertEquals("Untitled Plan", plan.title)
    }

    @Test
    fun `an unauthorized plans request is told apart from a failure`() {
        respondWith("""{}""", HttpStatusCode.Unauthorized)
        assertEquals(
            PlanningCenterClient.PlansOutcome.Unauthorized,
            runBlocking { PlanningCenterClient.listUpcomingPlans("tok", "svc-1", http = http) },
        )
    }

    @Test
    fun `a server error listing plans is a plain failure`() {
        respondWith("""{}""", HttpStatusCode.InternalServerError)
        assertEquals(
            PlanningCenterClient.PlansOutcome.Failure,
            runBlocking { PlanningCenterClient.listUpcomingPlans("tok", "svc-1", http = http) },
        )
    }

    @Test
    fun `an unreachable services endpoint is a network error listing plans`() {
        failToConnect()
        assertEquals(
            PlanningCenterClient.PlansOutcome.NetworkError,
            runBlocking { PlanningCenterClient.listUpcomingPlans("tok", "svc-1", http = http) },
        )
    }

    // ── Plan items ──────────────────────────────────────────────────────────────

    @Test
    fun `a song item picks up its song's title, author and ccli number from the included array`() {
        respondWith(
            """{
                "data":[{
                    "id":"item-1",
                    "attributes":{
                        "title":"Amazing Grace","description":"desc","html_details":"<p>hi</p>","item_type":"song","sequence":2
                    },
                    "relationships":{
                        "song":{"data":{"type":"Song","id":"song-1"}},
                        "arrangement":{"data":{"type":"Arrangement","id":"arr-1"}}
                    }
                }],
                "included":[{
                    "type":"Song","id":"song-1",
                    "attributes":{"title":"Amazing Grace","author":"John Newton","ccli_number":"123456"}
                }]
            }""",
        )

        val item = assertIs<PlanningCenterClient.PlanItemsOutcome.Success>(
            runBlocking { PlanningCenterClient.getPlanItems("tok", "svc-1", "plan-1", http = http) },
        ).items.single()

        assertEquals("item-1", item.id)
        assertEquals("Amazing Grace", item.title)
        assertEquals("desc", item.description)
        assertEquals("<p>hi</p>", item.htmlDetails)
        assertEquals("song", item.itemType)
        assertEquals(2, item.sequence)
        assertEquals("song-1", item.songId)
        assertEquals("arr-1", item.arrangementId)
        assertEquals("Amazing Grace", item.songTitle)
        assertEquals("John Newton", item.songAuthor)
        assertEquals("123456", item.songCcliNumber)
    }

    @Test
    fun `items are returned in plan order regardless of the order the api sent them`() {
        respondWith(
            """{"data":[
                {"id":"b","attributes":{"title":"Second","item_type":"header","sequence":2}},
                {"id":"a","attributes":{"title":"First","item_type":"header","sequence":1}}],
              "included":[]}""",
        )

        val items = assertIs<PlanningCenterClient.PlanItemsOutcome.Success>(
            runBlocking { PlanningCenterClient.getPlanItems("tok", "svc-1", "plan-1", http = http) },
        ).items

        assertEquals(listOf("a", "b"), items.map { it.id })
    }

    @Test
    fun `a non-song item has no song fields and defaults its type and sequence`() {
        respondWith("""{"data":[{"id":"h1","attributes":{"title":"Welcome"}}],"included":[]}""")

        val item = assertIs<PlanningCenterClient.PlanItemsOutcome.Success>(
            runBlocking { PlanningCenterClient.getPlanItems("tok", "svc-1", "plan-1", http = http) },
        ).items.single()

        assertEquals("item", item.itemType, "PCO omits item_type for generic items")
        assertEquals(0, item.sequence)
        assertNull(item.songId)
        assertNull(item.arrangementId)
        assertNull(item.songTitle)
    }

    @Test
    fun `a song relationship with nothing matching in the included array leaves song fields absent`() {
        respondWith(
            """{"data":[{
                "id":"item-1","attributes":{"title":"Song without details","item_type":"song"},
                "relationships":{"song":{"data":{"type":"Song","id":"song-missing"}}}
              }],"included":[]}""",
        )

        val item = assertIs<PlanningCenterClient.PlanItemsOutcome.Success>(
            runBlocking { PlanningCenterClient.getPlanItems("tok", "svc-1", "plan-1", http = http) },
        ).items.single()

        assertEquals("song-missing", item.songId, "the relationship itself is still recorded")
        assertNull(item.songTitle, "but nothing was included to read a title from")
    }

    @Test
    fun `an unauthorized plan items request is told apart from a failure`() {
        respondWith("""{}""", HttpStatusCode.Unauthorized)
        assertEquals(
            PlanningCenterClient.PlanItemsOutcome.Unauthorized,
            runBlocking { PlanningCenterClient.getPlanItems("tok", "svc-1", "plan-1", http = http) },
        )
    }

    @Test
    fun `a server error listing plan items is a plain failure`() {
        respondWith("""{}""", HttpStatusCode.InternalServerError)
        assertEquals(
            PlanningCenterClient.PlanItemsOutcome.Failure,
            runBlocking { PlanningCenterClient.getPlanItems("tok", "svc-1", "plan-1", http = http) },
        )
    }

    @Test
    fun `an unreachable services endpoint is a network error listing plan items`() {
        failToConnect()
        assertEquals(
            PlanningCenterClient.PlanItemsOutcome.NetworkError,
            runBlocking { PlanningCenterClient.getPlanItems("tok", "svc-1", "plan-1", http = http) },
        )
    }

    // ── Arrangement lyrics ──────────────────────────────────────────────────────

    @Test
    fun `pco's own lyrics attribute is preferred over locally stripping the chord chart`() {
        respondWith(
            """{"data":{"attributes":{"chord_chart":"[G]Amazing [C]grace","lyrics":"Amazing grace (server stripped)"}}}""",
        )

        val detail = assertIs<PlanningCenterClient.ArrangementOutcome.Success>(
            runBlocking { PlanningCenterClient.getArrangementDetail("tok", "song-1", "arr-1", http = http) },
        ).detail

        assertEquals("[G]Amazing [C]grace", detail.chordChart)
        assertEquals("Amazing grace (server stripped)", detail.lyrics)
    }

    @Test
    fun `lyrics are derived from the chord chart when pco's own lyrics attribute is blank`() {
        respondWith("""{"data":{"attributes":{"chord_chart":"[G]Amazing [C]grace","lyrics":""}}}""")

        val detail = assertIs<PlanningCenterClient.ArrangementOutcome.Success>(
            runBlocking { PlanningCenterClient.getArrangementDetail("tok", "song-1", "arr-1", http = http) },
        ).detail

        assertEquals(PlanningCenterLyricsFormatter.stripChords("[G]Amazing [C]grace"), detail.lyrics)
    }

    @Test
    fun `lyrics are derived from the chord chart when pco's own lyrics attribute is absent`() {
        respondWith("""{"data":{"attributes":{"chord_chart":"[G]Amazing [C]grace"}}}""")

        val detail = assertIs<PlanningCenterClient.ArrangementOutcome.Success>(
            runBlocking { PlanningCenterClient.getArrangementDetail("tok", "song-1", "arr-1", http = http) },
        ).detail

        assertEquals("Amazing grace", detail.lyrics)
    }

    @Test
    fun `an unauthorized arrangement request is told apart from a failure`() {
        respondWith("""{}""", HttpStatusCode.Unauthorized)
        assertEquals(
            PlanningCenterClient.ArrangementOutcome.Unauthorized,
            runBlocking { PlanningCenterClient.getArrangementDetail("tok", "song-1", "arr-1", http = http) },
        )
    }

    @Test
    fun `a server error fetching an arrangement is a plain failure`() {
        respondWith("""{}""", HttpStatusCode.InternalServerError)
        assertEquals(
            PlanningCenterClient.ArrangementOutcome.Failure,
            runBlocking { PlanningCenterClient.getArrangementDetail("tok", "song-1", "arr-1", http = http) },
        )
    }

    @Test
    fun `an unreachable services endpoint is a network error fetching an arrangement`() {
        failToConnect()
        assertEquals(
            PlanningCenterClient.ArrangementOutcome.NetworkError,
            runBlocking { PlanningCenterClient.getArrangementDetail("tok", "song-1", "arr-1", http = http) },
        )
    }

    // ── Item attachments ────────────────────────────────────────────────────────

    @Test
    fun `an attachment is read with its thumbnail when one is present`() {
        respondWith(
            """{"data":[{"id":"att-1","attributes":{"filename":"slides.pdf","thumbnail_url":"https://s3/thumb.jpg"}}]}""",
        )

        val attachment = assertIs<PlanningCenterClient.AttachmentsOutcome.Success>(
            runBlocking { PlanningCenterClient.getItemAttachments("tok", "svc-1", "plan-1", "item-1", http = http) },
        ).attachments.single()

        assertEquals(PlanningCenterClient.PlanAttachment("att-1", "slides.pdf", "https://s3/thumb.jpg"), attachment)
    }

    @Test
    fun `an attachment with no thumbnail is still listed`() {
        respondWith("""{"data":[{"id":"att-1","attributes":{"filename":"slides.pdf"}}]}""")

        val attachment = assertIs<PlanningCenterClient.AttachmentsOutcome.Success>(
            runBlocking { PlanningCenterClient.getItemAttachments("tok", "svc-1", "plan-1", "item-1", http = http) },
        ).attachments.single()

        assertNull(attachment.thumbnailUrl)
    }

    @Test
    fun `an attachment missing a filename is left out rather than shown blank`() {
        respondWith(
            """{"data":[
                {"id":"att-1","attributes":{}},
                {"id":"att-2","attributes":{"filename":"ok.pdf"}}]}""",
        )

        val attachments = assertIs<PlanningCenterClient.AttachmentsOutcome.Success>(
            runBlocking { PlanningCenterClient.getItemAttachments("tok", "svc-1", "plan-1", "item-1", http = http) },
        ).attachments

        assertEquals(listOf("att-2"), attachments.map { it.id })
    }

    @Test
    fun `an unauthorized attachments request is told apart from a failure`() {
        respondWith("""{}""", HttpStatusCode.Unauthorized)
        assertEquals(
            PlanningCenterClient.AttachmentsOutcome.Unauthorized,
            runBlocking { PlanningCenterClient.getItemAttachments("tok", "svc-1", "plan-1", "item-1", http = http) },
        )
    }

    @Test
    fun `a server error listing attachments is a plain failure`() {
        respondWith("""{}""", HttpStatusCode.InternalServerError)
        assertEquals(
            PlanningCenterClient.AttachmentsOutcome.Failure,
            runBlocking { PlanningCenterClient.getItemAttachments("tok", "svc-1", "plan-1", "item-1", http = http) },
        )
    }

    @Test
    fun `an unreachable services endpoint is a network error listing attachments`() {
        failToConnect()
        assertEquals(
            PlanningCenterClient.AttachmentsOutcome.NetworkError,
            runBlocking { PlanningCenterClient.getItemAttachments("tok", "svc-1", "plan-1", "item-1", http = http) },
        )
    }

    // ── Resolving an attachment's download url ──────────────────────────────────

    @Test
    fun `an attachment open resolves to its download url`() {
        respondWith("""{"data":{"attributes":{"attachment_url":"https://s3/signed-link"}}}""")

        val outcome = runBlocking { PlanningCenterClient.resolveAttachmentDownloadUrl("tok", "att-1", http = http) }

        assertEquals("https://s3/signed-link", assertIs<PlanningCenterClient.AttachmentUrlOutcome.Success>(outcome).url)
    }

    @Test
    fun `an attachment open response with no url attribute is a failure`() {
        respondWith("""{"data":{"attributes":{}}}""")

        assertEquals(
            PlanningCenterClient.AttachmentUrlOutcome.Failure,
            runBlocking { PlanningCenterClient.resolveAttachmentDownloadUrl("tok", "att-1", http = http) },
        )
    }

    @Test
    fun `an unauthorized attachment open is told apart from a failure`() {
        respondWith("""{}""", HttpStatusCode.Unauthorized)
        assertEquals(
            PlanningCenterClient.AttachmentUrlOutcome.Unauthorized,
            runBlocking { PlanningCenterClient.resolveAttachmentDownloadUrl("tok", "att-1", http = http) },
        )
    }

    @Test
    fun `a server error opening an attachment is a plain failure`() {
        respondWith("""{}""", HttpStatusCode.InternalServerError)
        assertEquals(
            PlanningCenterClient.AttachmentUrlOutcome.Failure,
            runBlocking { PlanningCenterClient.resolveAttachmentDownloadUrl("tok", "att-1", http = http) },
        )
    }

    @Test
    fun `an unreachable services endpoint opening an attachment is a failure, not a distinct network outcome`() {
        // Unlike the other Services calls, AttachmentUrlOutcome has no NetworkError case —
        // a dropped connection here reads the same as a server error.
        failToConnect()
        assertEquals(
            PlanningCenterClient.AttachmentUrlOutcome.Failure,
            runBlocking { PlanningCenterClient.resolveAttachmentDownloadUrl("tok", "att-1", http = http) },
        )
    }

    @Test
    fun `opening an attachment is a post, not a get`() {
        respondWith("""{"data":{"attributes":{"attachment_url":"https://s3/link"}}}""")

        runBlocking { PlanningCenterClient.resolveAttachmentDownloadUrl("tok", "att-1", http = http) }

        assertEquals(HttpMethod.Post, requests.single().method)
    }
}
