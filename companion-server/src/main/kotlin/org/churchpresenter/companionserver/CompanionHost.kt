package org.churchpresenter.companionserver

import java.io.File
import kotlinx.coroutines.flow.SharedFlow
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.songs.SongItem

/**
 * What the server needs from the desktop app it is embedded in, and the only place it needs it.
 *
 * Everything here is something the app owns and this module deliberately does not: the version it
 * reports itself as, its usage counters, its image decoders, its song loader, and the Skia
 * composition that turns a lottie file into pixels. Each is a value rather than a dependency so the
 * module compiles without Compose and a test can start a real server without any of them — the
 * defaults are inert, and a route that needs one answers "not available" rather than failing.
 *
 * Add to this only what genuinely cannot live here. A new field is a new thing the server knows
 * about the app, and the point of the module is that the list is short.
 */
class CompanionHost(
    /** Reported in the `Server-Version` header and by `GET /api/info`. Blank in tests. */
    val appVersion: String = "",
    /** Called the first time a phone (not a follower instance) opens the WebSocket. */
    val onMobileClientConnected: () -> Unit = {},
    /** Turns a HEIC picture into JPEG bytes for browsers that cannot display it. */
    val decodeHeicToJpeg: (File) -> ByteArray? = { null },
    /** Reads one `.sps` song file. Used only by [CompanionServer.preloadData]. */
    val loadSongs: (File) -> List<SongItem> = { emptyList() },
    /** Draws lower-third lottie frames. See [LottieFrameRenderer]. */
    val lottieRenderer: LottieFrameRenderer = LottieFrameRenderer { _, _, _, _, _ -> },
)

/**
 * Draws one lower-third lottie animation to raw ARGB pixels.
 *
 * The real implementation composes offscreen with Skia, so it needs Compose and lives in the app;
 * this module holds only the cache format written around it. [LottieRenderCache.prepare] takes one
 * explicitly rather than reading a global, so a test renders deterministic frames without a GPU and
 * a forgotten wiring is a compile error rather than a screen full of blank lower thirds.
 *
 * [block] is called once with a `renderFrame` function it may call repeatedly; the returned
 * `IntArray` is `width * height` pixels of `(A shl 24) or (R shl 16) or (G shl 8) or B` and is
 * reused between calls, so copy it to retain it.
 */
fun interface LottieFrameRenderer {
    suspend fun withSession(
        width: Int,
        height: Int,
        lottieJson: String,
        initialProgress: Float,
        block: suspend (renderFrame: suspend (Float) -> IntArray) -> Unit,
    )
}

/**
 * The question list behind the Q&A routes, as the server sees it.
 *
 * The app's `QAManager` implements it. The interface exists so the server does not hold a view
 * model — it used to hold `QAManager` itself, which both crossed the module boundary and broke the
 * repo's rule against passing a view model into another class. Everything here is called by
 * [qaRoutes]; nothing else about Q&A is the server's business.
 */
interface QaStore {
    /** Emits once per change so the server can tell connected clients to re-fetch. */
    val events: SharedFlow<*>
    val questions: List<Question>
    val sessionActive: Boolean
    val displayedQuestion: Question?

    fun submitQuestion(
        text: String,
        name: String = "",
        clientIp: String = "",
        cooldownSeconds: Int = 30,
        deviceId: String = "",
        timestamp: Long = System.currentTimeMillis(),
    ): Question?

    fun addQuestion(text: String, timestamp: Long = System.currentTimeMillis()): Question?
    fun findQuestion(id: String): Question?
    fun getApprovedQuestions(): List<Question>
    fun approveQuestion(id: String): Boolean
    fun denyQuestion(id: String): Boolean
    fun markDone(id: String): Boolean
    fun editQuestion(id: String, newText: String): Boolean
    fun deleteQuestion(id: String): Boolean
    fun displayQuestion(id: String): Boolean
    fun voteForQuestion(questionId: String, clientIp: String, direction: String = "up"): Boolean
    fun getVoteDirection(questionId: String, clientIp: String): String?
    fun isRateLimited(clientIp: String, cooldownSeconds: Int): Boolean
    fun clearDisplay()
    fun clearAll()
}
