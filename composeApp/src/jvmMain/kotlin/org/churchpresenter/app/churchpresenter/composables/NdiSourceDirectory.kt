package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.app.churchpresenter.presenter.NdiManager
import org.churchpresenter.ndi.NdiFinder
import org.churchpresenter.ndi.NdiSourceInfo

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

    private var finder: NdiFinder? = null
    private var refCount = 0

    /** Whether discovery is currently running. */
    @get:Synchronized
    val isRunning: Boolean get() = finder != null

    /** Starts discovery if it is not already running. Match every call with a [release]. */
    @Synchronized
    fun acquire() {
        refCount++
        if (finder != null) return
        finder = openFinder()?.takeIf { it.open() }
    }

    /** Stops discovery when the last caller lets go. */
    @Synchronized
    fun release() {
        refCount--
        if (refCount > 0) return
        refCount = 0
        finder?.close()
        finder = null
    }

    /**
     * The sources discovery knows about, waiting [waitMs] for the network first.
     *
     * Blocks for up to that long, so callers run it off the composition. An empty list from a young
     * finder means "not yet", not "nobody is sending" — which is why the picker says *no sources
     * found* only after it has actually looked.
     */
    fun sources(waitMs: Int = FIRST_LOOK_MS): List<NdiSourceInfo> =
        (synchronized(this) { finder })?.sources(waitMs)?.filter { it.name.isNotBlank() }.orEmpty()
}

/** The one directory the Canvas picker looks in, over the app's one runtime. */
object SharedNdiSources : NdiSourceDirectory(openFinder = { NdiManager.createFinder() })
