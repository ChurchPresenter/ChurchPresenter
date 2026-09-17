package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.bibleformats.catalog.BibleSourceId
import org.churchpresenter.bibleformats.catalog.InstallPhase
import org.churchpresenter.app.churchpresenter.viewmodel.BibleCatalogError
import org.churchpresenter.app.churchpresenter.viewmodel.BibleDownloadError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The five `when`s that turn a catalogue's enums into the strings the download browser shows.
 *
 * Compared by `StringResource.key` rather than by identity: `Res.string.x` builds its resource on
 * each access, so two reads of the same name are equal in the only sense that matters here and are
 * not the same object.
 *
 * Every case is driven from the enum's own `entries`, so adding a value to any of these enums fails
 * the distinctness assertion here as well as the exhaustive `when` — which is the point. A new
 * source or a new failure that quietly reused an existing sentence would compile.
 */
class BibleCatalogLabelsTest {

    @Test
    fun `each install phase names itself`() {
        val keys = InstallPhase.entries.associateWith { phaseStringRes(it).key }
        assertEquals(
            InstallPhase.entries.size,
            keys.values.toSet().size,
            "each phase must have a sentence of its own: $keys",
        )
    }

    @Test
    fun `no phase falls back to downloading, and a missing one does`() {
        // The `else` branch stands for "no progress reported yet", which the dialog shows as the
        // download it is about to start rather than as a blank.
        assertEquals(
            phaseStringRes(InstallPhase.DOWNLOADING).key,
            phaseStringRes(null).key,
            "an unreported phase reads as the download itself",
        )
        for (phase in InstallPhase.entries - InstallPhase.DOWNLOADING) {
            assertNotEquals(
                phaseStringRes(null).key,
                phaseStringRes(phase).key,
                "$phase must not be swallowed by the fallback",
            )
        }
    }

    @Test
    fun `each source has a name and a licence line of its own`() {
        val names = BibleSourceId.entries.associateWith { sourceLabelStringRes(it).key }
        val licences = BibleSourceId.entries.associateWith { sourceLicenceStringRes(it).key }
        assertEquals(BibleSourceId.entries.size, names.values.toSet().size, "names collide: $names")
        assertEquals(BibleSourceId.entries.size, licences.values.toSet().size, "licences collide: $licences")
    }

    @Test
    fun `a source's licence line is not its name`() {
        for (source in BibleSourceId.entries) {
            assertNotEquals(
                sourceLabelStringRes(source).key,
                sourceLicenceStringRes(source).key,
                "$source must not offer its own name where its copyright belongs",
            )
        }
    }

    @Test
    fun `each way of failing to reach a catalogue reads differently`() {
        val keys = BibleCatalogError.entries.associateWith { catalogErrorStringRes(it).key }
        assertEquals(BibleCatalogError.entries.size, keys.values.toSet().size, "collides: $keys")
    }

    @Test
    fun `each way an install can fail reads differently`() {
        val keys = BibleDownloadError.entries.associateWith { installErrorStringRes(it).key }
        assertEquals(BibleDownloadError.entries.size, keys.values.toSet().size, "collides: $keys")
    }

    @Test
    fun `a stalled download does not read as a dead network`() {
        // The two are deliberately separate: only the stall is worth offering a retry for, so a
        // reader who is told "check your connection" for a stall is being sent the wrong way.
        assertNotEquals(
            installErrorStringRes(BibleDownloadError.NETWORK_ERROR).key,
            installErrorStringRes(BibleDownloadError.DOWNLOAD_STALLED).key,
        )
    }
}
