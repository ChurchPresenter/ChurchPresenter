package org.churchpresenter.companionserver

import kotlinx.serialization.json.Json
import org.churchpresenter.settings.AtemSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which keyer a request is allowed to drive, and which one it ends up driving.
 *
 * Both answers are decided before a single byte reaches the switcher, and both are worth pinning on
 * their own: `validateKeyTarget` is the only thing standing between a typo'd slot number and a
 * command sent to hardware that has no such keyer, and `resolveUseDsk` decides whether a "go live"
 * cuts an upstream key over the programme or a downstream key over everything.
 *
 * A switcher that has not been reached yet reports zero of everything — `detectedMixEffects`,
 * `detectedKeyersPerMe` and `detectedDownstreamKeyers` all start empty. **Zero means "not known
 * yet", not "none", so nothing may be rejected on that basis**: refusing a key because the switcher
 * has not answered its state dump would break every request made in the first second of a
 * connection. Each of those three is a test here.
 */
class AtemKeyTargetTest {

    private val bridge = AtemBridge(Json)

    private fun settings(
        mixEffects: Int = 0,
        keyersPerMe: List<Int> = emptyList(),
        downstreamKeyers: Int = 0,
        useDownstreamKey: Boolean = false,
    ) = AtemSettings(
        host = "127.0.0.1",
        detectedMixEffects = mixEffects,
        detectedKeyersPerMe = keyersPerMe,
        detectedDownstreamKeyers = downstreamKeyers,
        useDownstreamKey = useDownstreamKey,
    )

    // ── validateKeyTarget: upstream ─────────────────────────────────────────────

    @Test
    fun `a known M-E and keyer are accepted`() {
        val err = bridge.validateKeyTarget(
            settings(mixEffects = 2, keyersPerMe = listOf(4, 2)), useDsk = false, mixEffect = 1, keyer = 1,
        )
        assertNull(err)
    }

    @Test
    fun `an M-E past the end of the switcher is named in the refusal`() {
        val err = bridge.validateKeyTarget(
            settings(mixEffects = 2, keyersPerMe = listOf(4, 4)), useDsk = false, mixEffect = 5, keyer = 0,
        )
        assertEquals("M/E 6 does not exist (available: 1-2)", err, "the operator sees 1-based numbers")
    }

    @Test
    fun `a keyer past the end of that M-E is named in the refusal`() {
        val err = bridge.validateKeyTarget(
            settings(mixEffects = 2, keyersPerMe = listOf(4, 1)), useDsk = false, mixEffect = 1, keyer = 3,
        )
        assertEquals("Key 4 does not exist on M/E 2 (available: 1-1)", err)
    }

    @Test
    fun `nothing is refused before the switcher has reported its M-E count`() {
        assertNull(
            bridge.validateKeyTarget(settings(mixEffects = 0), useDsk = false, mixEffect = 3, keyer = 2),
            "zero M/Es means the state dump has not arrived, not that the switcher has none",
        )
    }

    @Test
    fun `an M-E the switcher has not described its keyers for is accepted`() {
        assertNull(
            bridge.validateKeyTarget(
                settings(mixEffects = 4, keyersPerMe = listOf(4)), useDsk = false, mixEffect = 2, keyer = 9,
            ),
            "the M/E exists; how many keyers it has is simply not known yet",
        )
    }

    @Test
    fun `an M-E reported as having no keyers refuses nothing`() {
        assertNull(
            bridge.validateKeyTarget(
                settings(mixEffects = 2, keyersPerMe = listOf(0, 0)), useDsk = false, mixEffect = 0, keyer = 2,
            ),
        )
    }

    // ── validateKeyTarget: downstream ───────────────────────────────────────────

    @Test
    fun `a known downstream keyer is accepted`() {
        assertNull(
            bridge.validateKeyTarget(settings(downstreamKeyers = 2), useDsk = true, mixEffect = 0, keyer = 1),
        )
    }

    @Test
    fun `a downstream keyer past the end is named in the refusal`() {
        val err = bridge.validateKeyTarget(
            settings(downstreamKeyers = 2), useDsk = true, mixEffect = 0, keyer = 4,
        )
        assertEquals("DSK 5 does not exist (available: 1-2)", err)
    }

    @Test
    fun `nothing is refused before the switcher has reported its DSK count`() {
        assertNull(
            bridge.validateKeyTarget(settings(downstreamKeyers = 0), useDsk = true, mixEffect = 0, keyer = 7),
        )
    }

    @Test
    fun `a downstream request is not judged against the M-E limits`() {
        assertNull(
            bridge.validateKeyTarget(
                settings(mixEffects = 1, keyersPerMe = listOf(1), downstreamKeyers = 4),
                useDsk = true, mixEffect = 9, keyer = 3,
            ),
            "a DSK has no M/E — the upstream checks must not run at all",
        )
    }

    // ── resolveUseDsk ───────────────────────────────────────────────────────────

    @Test
    fun `keytype dsk drives a downstream key whatever the setting says`() {
        assertTrue(bridge.resolveUseDsk("dsk", settings(useDownstreamKey = false)))
        assertTrue(bridge.resolveUseDsk("downstream", settings(useDownstreamKey = false)))
    }

    @Test
    fun `keytype usk drives an upstream key whatever the setting says`() {
        assertFalse(bridge.resolveUseDsk("usk", settings(useDownstreamKey = true)))
        assertFalse(bridge.resolveUseDsk("upstream", settings(useDownstreamKey = true)))
    }

    @Test
    fun `the override is case-insensitive`() {
        assertTrue(bridge.resolveUseDsk("DSK", settings(useDownstreamKey = false)))
        assertFalse(bridge.resolveUseDsk("Upstream", settings(useDownstreamKey = true)))
    }

    @Test
    fun `no keytype falls back to the persisted setting`() {
        assertTrue(bridge.resolveUseDsk(null, settings(useDownstreamKey = true)))
        assertFalse(bridge.resolveUseDsk(null, settings(useDownstreamKey = false)))
    }

    @Test
    fun `a keytype nobody recognises falls back to the setting rather than guessing`() {
        assertTrue(bridge.resolveUseDsk("sideways", settings(useDownstreamKey = true)))
        assertFalse(bridge.resolveUseDsk("", settings(useDownstreamKey = false)))
    }
}
