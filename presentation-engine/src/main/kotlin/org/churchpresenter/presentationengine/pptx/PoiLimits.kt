package org.churchpresenter.presentationengine.pptx

import org.apache.poi.util.IOUtils
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Raises the ceiling POI puts on a single allocated byte array, once per process.
 *
 * POI refuses to allocate more than [DEFAULT_LIMIT_BYTES] for one record and throws
 * `RecordFormatException` instead. That default is a guard against a corrupt or hostile file
 * declaring an absurd length, and it is the right shape of protection — but real decks exceed it:
 * a picture pasted at camera resolution and never downsampled clears 100 MB on its own, and when
 * `XSLFPictureData.getData` throws, the throw lands inside POI's drawing code and takes the whole
 * slide with it.
 *
 * So the limit is raised rather than removed. [MAX_RECORD_BYTES] is deliberately finite: passing
 * `-1` disables the check altogether, which trades a blank slide for a declared length large enough
 * to OOM the app — a worse failure, and one that takes the live service down instead of one image.
 *
 * POI exposes only a setter for this — there is no `getByteArrayMaxOverride` to read the current
 * value back — so the one-shot latch is what keeps this from clobbering a later, deliberate
 * override rather than a comparison.
 */
internal object PoiLimits {

    /** POI's own default, and the number quoted in the exception message when it trips. */
    const val DEFAULT_LIMIT_BYTES: Int = 100_000_000

    /** Generous enough for an un-downsampled photograph, small enough to stay allocatable. */
    const val MAX_RECORD_BYTES: Int = 512 * 1024 * 1024

    private val applied = AtomicBoolean(false)

    /** Whether [apply] has already run. The override itself has no getter in POI to read back. */
    internal val hasApplied: Boolean get() = applied.get()

    /**
     * Applies the override the first time it is called and does nothing afterwards.
     *
     * Idempotent because it is called from [PowerPointDeckSupport.open], which runs once per deck
     * on whichever thread opened it — the `compareAndSet` is what makes concurrent opens safe, and
     * it follows the same shape as `SlideFontRegistry.initialize`.
     */
    fun apply() {
        if (!applied.compareAndSet(false, true)) return
        IOUtils.setByteArrayMaxOverride(MAX_RECORD_BYTES)
    }

    /** Resets the one-shot latch. Tests only — the override itself is process-global. */
    internal fun resetForTest() = applied.set(false)
}
