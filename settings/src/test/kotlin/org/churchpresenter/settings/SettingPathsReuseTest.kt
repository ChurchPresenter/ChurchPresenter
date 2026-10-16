package org.churchpresenter.settings

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A profile's values by path are flattened once per profile, not once per value read: the profile
 * pages read them a row at a time, many times a composition.
 */
class SettingPathsReuseTest {

    @Test
    fun `reading one profile twice flattens it once`() {
        val profile = OutputProfile(id = "reuse")

        assertSame(profile.settingPaths(), profile.settingPaths())
    }

    @Test
    fun `a changed copy reads its own values, not the original's`() {
        val profile = OutputProfile(id = "reuse", showChords = true)
        profile.settingPaths()
        val changed = profile.copy(showChords = false)

        assertEquals(JsonPrimitive(true), profile.valueAt("showChords"))
        assertEquals(JsonPrimitive(false), changed.valueAt("showChords"))
    }

    @Test
    fun `an equal profile that is another instance reads the same values`() {
        val one = OutputProfile(id = "reuse")
        val other = OutputProfile(id = "reuse")

        assertEquals(one.settingPaths(), other.settingPaths())
    }

    @Test
    fun `a profile pushed out by newer ones still reads its values`() {
        val first = OutputProfile(id = "first", showChords = false)
        first.settingPaths()
        repeat(20) { OutputProfile(id = "filler-$it").settingPaths() }

        assertEquals(JsonPrimitive(false), first.valueAt("showChords"))
    }
}
