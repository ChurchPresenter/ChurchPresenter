package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.app.churchpresenter.presenter.NdiManager
import org.churchpresenter.ndi.NdiFinder
import org.churchpresenter.ndi.NdiSourceInfo
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * How long a first look waits for the network to answer.
 *
 * Discovery is cumulative — mDNS replies trickle in over the first seconds of a finder's life — so
 * an immediate first query reliably returns nothing at all. Waiting here is what makes opening the
 * picker show the sources that are already there rather than an empty list that fills in later.
 */
private const val FIRST_LOOK_MS = 1_000

/**
 * Who is sending on the network, for the Canvas source picker.
 *
 * **The finder is kept open between looks, which is the whole point of this class.** The SDK builds
 * its picture up over time and forgets it when the finder is destroyed, so a finder created per
 * query would report an empty network every single time. It is reference counted instead: opened
 * when a properties panel starts looking, closed when the last one goes away, so nothing is
 * answering mDNS for a panel that closed an hour ago.
 *
 * A class rather than the object it is reached through, so a test drives it over a fake library.
 */
open class NdiSourceDirectory(private val openFinder: () -> NdiFinder?) {

    /**
     * One finder, and the looks currently inside it.
     *
     * Bound to the finder rather than to the directory because a look outlives the panel that
     * started it: when the last panel lets go and another opens before the look returns, a new
     * finder exists, and the returning look must close the finder it actually entered -- not the
     * live one. Holding the reference is what makes reaching the wrong one impossible.
     */
    private class Discovery(val finder: NdiFinder) {
        /** One look at a time through this finder; [NdiFinder] is driven by a single owner. */
        val lookups = ReentrantLock()

        /** Looks that have entered [NdiFinder.sources] and not yet left. Guarded by the monitor. */
        var inFlight = 0

        /** Set when the last panel let go; the finder closes when [inFlight] reaches zero. */
        var retired = false
    }

    private var current: Discovery? = null
    private var refCount = 0

    /** Whether discovery is running and accepting new looks. */
    @get:Synchronized
    val isRunning: Boolean get() = current != null

    /** Starts discovery if it is not already running. Match every call with a [release]. */
    @Synchronized
    fun acquire() {
        refCount++
        if (current != null) return
        current = openFinder()?.takeIf { it.open() }?.let(::Discovery)
    }

    /**
     * Stops discovery when the last caller lets go.
     *
     * The finder is retired here but destroyed only once no look is inside it. Destroying one that
     * a look is parked in is a use-after-free, and a fatal native crash rather than an exception:
     * the caller here is the composition disposing a properties panel, while the look it cannot
     * interrupt is inside the runtime for up to a second. The last look out closes it instead, on
     * the background thread it is already running on.
     */
    @Synchronized
    fun release() {
        refCount--
        if (refCount > 0) return
        refCount = 0
        val retiring = current ?: return
        current = null
        retiring.retired = true
        // Nobody is inside it, so nobody is left to close it -- and closing here costs no wait.
        if (retiring.inFlight == 0) retiring.finder.close()
    }

    /**
     * The sources discovery knows about, waiting [waitMs] for the network first.
     *
     * Blocks for up to that long, so callers run it off the composition. An empty list from a young
     * finder means "not yet", not "nobody is sending" -- which is why the picker says *no sources
     * found* only after it has actually looked.
     */
    fun sources(waitMs: Int = FIRST_LOOK_MS): List<NdiSourceInfo> {
        val discovery = synchronized(this) { current?.also { it.inFlight++ } } ?: return emptyList()
        try {
            // Serialised on a lock of the finder's own, which release() never takes -- so the
            // disposing thread is never made to wait, and two looks never share one finder. The
            // array the runtime hands back belongs to that finder until the next call on it.
            return discovery.lookups.withLock { discovery.finder.sources(waitMs) }
                .filter { it.name.isNotBlank() }
        } finally {
            leave(discovery)
        }
    }

    /** Leaves a look, closing the finder when this was the last one out of a retired [discovery]. */
    private fun leave(discovery: Discovery) {
        val closing = synchronized(this) {
            discovery.inFlight--
            discovery.retired && discovery.inFlight == 0
        }
        if (closing) discovery.finder.close()
    }
}

/** The one directory the Canvas picker looks in, over the app's one runtime. */
object SharedNdiSources : NdiSourceDirectory(openFinder = { NdiManager.createFinder() })
