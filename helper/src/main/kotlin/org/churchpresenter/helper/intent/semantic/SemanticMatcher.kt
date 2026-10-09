package org.churchpresenter.helper.intent.semantic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.diagnostics.Log
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** How close one catalog target came to a request: the best of its entries, as a cosine. */
internal data class Scored(val target: CatalogTarget, val score: Float)

/**
 * The model and the catalog together: given what a request says, every catalog target ranked by how close
 * its texts come in meaning.
 *
 * Runs on one thread of its own at the lowest priority, so a request never takes time from the UI or the
 * output renderers. The model loads on the first request and is let go after [idle] without one.
 */
internal class SemanticMatcher(
    private val loadEncoder: () -> MiniLmEncoder = MiniLmEncoder::load,
    private val loadCatalog: () -> List<CatalogEntry> = WickCatalog::load,
    private val idle: Duration = 10.minutes,
) {
    private var encoder: MiniLmEncoder? = null
    private var catalog: List<CatalogEntry> = emptyList()
    private var release: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /**
     * The catalog's targets, best first, for whichever of [readings] each comes closest to — or null when
     * the model could not be loaded, which leaves the helper as it was without it.
     */
    suspend fun rank(readings: List<String>): List<Scored>? = withContext(dispatcher) {
        val model = encoder ?: load() ?: return@withContext null
        keepFor(idle)
        val queries = readings.map(model::encode)
        catalog.groupBy { it.target }
            .map { (target, entries) ->
                Scored(target, entries.maxOf { entry -> queries.maxOf { cosine(it, entry.vector) } })
            }
            .sortedByDescending { it.score }
    }

    private fun load(): MiniLmEncoder? = runCatching {
        catalog = loadCatalog()
        loadEncoder()
    }.onFailure { Log.warn("Wick", "Could not load the model, so requests are read by the rules alone: $it") }
        .getOrNull()
        ?.also { encoder = it }

    /** Lets the model go once [idle] passes with no request. */
    private fun keepFor(idle: Duration) {
        release?.cancel()
        release = scope.launch {
            delay(idle)
            encoder = null
            catalog = emptyList()
        }
    }

    private companion object {
        /** One daemon thread at the lowest priority, shared by every matcher. */
        @OptIn(ExperimentalCoroutinesApi::class)
        val dispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "wick-model").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }.asCoroutineDispatcher()
    }
}
