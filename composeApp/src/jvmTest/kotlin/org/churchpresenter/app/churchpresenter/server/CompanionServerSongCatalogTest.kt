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
import org.churchpresenter.calendar.sync.CatalogRecord
import org.churchpresenter.calendar.sync.CatalogSong
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionServerSongCatalogTest {

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

    private suspend fun catalog(apiKey: String? = null) = client.get(
        "http://127.0.0.1:$port${Constants.ENDPOINT_SONG_CATALOG}",
    ) {
        if (apiKey != null) header(Constants.HEADER_API_KEY, apiKey)
    }

    @Test
    fun `every songbook is listed with its songs and their measured lengths`() = runBlocking<Unit> {
        server.updateSongs(
            listOf(
                song("42", "Here I Am to Worship").copy(secondaryTitle = "Вот я, Господь"),
                song("7", "Never Sung"),
                song("", "Untitled Chorus"),
                song("1", "Shout", songbook = "Praise"),
            ),
        )
        server.typicalSeconds = { s ->
            when (s.title) {
                "Here I Am to Worship" -> 270
                "Untitled Chorus" -> 95
                else -> null
            }
        }

        val response = catalog()
        assertEquals(HttpStatusCode.OK, response.status)
        val text = response.bodyAsText()
        // A song with no second title and no measured length carries neither, not a null for each.
        assertFalse("null" in text, text)
        assertTrue(""""t2":"Вот я, Господь"""" in text, text)
        val books = json.decodeFromString(SongCatalogRecordsResponse.serializer(), text).books

        assertEquals(
            listOf(
                CatalogRecord(
                    "Hymnal",
                    songs = listOf(
                        CatalogSong("7", "Never Sung"),
                        CatalogSong("42", "Here I Am to Worship", 270, t2 = "Вот я, Господь"),
                        CatalogSong("", "Untitled Chorus", 95),
                    ),
                ),
                CatalogRecord("Praise", songs = listOf(CatalogSong("1", "Shout"))),
            ),
            books,
        )
    }

    @Test
    fun `no songs means no books, not an error`() = runBlocking<Unit> {
        val response = catalog()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"books":[]}""", response.bodyAsText())
    }

    @Test
    fun `the api key gate applies`() = runBlocking<Unit> {
        server.updateApiKey(enabled = true, key = "secret")

        assertEquals(HttpStatusCode.Unauthorized, catalog().status)
        assertEquals(HttpStatusCode.OK, catalog(apiKey = "secret").status)
    }
}
