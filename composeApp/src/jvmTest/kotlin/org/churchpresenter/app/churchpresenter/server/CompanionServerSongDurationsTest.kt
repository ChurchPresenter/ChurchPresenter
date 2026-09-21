package org.churchpresenter.app.churchpresenter.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.testPort
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionServerSongDurationsTest {

    private lateinit var server: CompanionServer
    private lateinit var client: HttpClient
    private var port: Int = 0
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        server = CompanionServer()
        server.start(port = testPort(39_910))
        port = runBlocking {
            withTimeoutOrNull(10_000) {
                while (!server.isRunning.value || server.serverUrl.value.isBlank()) delay(25)
                server.serverUrl.value.substringAfterLast(':').toInt()
            }
        } ?: error("server did not start")
        client = HttpClient(CIO)
    }

    @AfterTest
    fun tearDown() {
        runCatching { client.close() }
        runCatching { server.stop() }
    }

    private fun song(number: String, title: String, songbook: String = "Hymnal") =
        SongItem(number = number, title = title, songbook = songbook)

    private suspend fun durations(apiKey: String? = null) = client.get(
        "http://127.0.0.1:$port${Constants.ENDPOINT_SONG_DURATIONS}",
    ) {
        if (apiKey != null) header(Constants.HEADER_API_KEY, apiKey)
    }

    @Test
    fun `only measured songs are listed, keyed the way the desktop keys them`() = runBlocking<Unit> {
        server.updateSongs(
            listOf(song("42", "Here I Am to Worship"), song("7", "Never Sung"), song("", "Untitled Chorus")),
        )
        server.typicalSeconds = { s ->
            when (s.title) {
                "Here I Am to Worship" -> 270
                "Untitled Chorus" -> 95
                else -> null
            }
        }

        val response = durations()
        assertEquals(HttpStatusCode.OK, response.status)
        val listed = json.decodeFromString(SongDurationsResponse.serializer(), response.bodyAsText()).durations

        assertEquals(
            listOf(
                SongDurationDto("Hymnal", "Hymnal::42", "Here I Am to Worship", 270),
                SongDurationDto("Hymnal", "Hymnal::Untitled Chorus", "Untitled Chorus", 95),
            ),
            listed,
        )
    }

    @Test
    fun `nothing measured means an empty list, not an error`() = runBlocking<Unit> {
        server.updateSongs(listOf(song("1", "Amazing Grace")))

        val response = durations()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"durations":[]}""", response.bodyAsText())
    }

    @Test
    fun `the api key gate applies`() = runBlocking<Unit> {
        server.updateApiKey(enabled = true, key = "secret")

        assertEquals(HttpStatusCode.Unauthorized, durations().status)
        assertEquals(HttpStatusCode.OK, durations(apiKey = "secret").status)
    }
}
