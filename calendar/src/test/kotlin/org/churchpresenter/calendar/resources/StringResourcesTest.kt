package org.churchpresenter.calendar.resources

import kotlinx.coroutines.runBlocking
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.allDrawableResources
import org.churchpresenter.calendar.generated.resources.allFontResources
import org.churchpresenter.calendar.generated.resources.allPluralStringResources
import org.churchpresenter.calendar.generated.resources.allStringArrayResources
import org.churchpresenter.calendar.generated.resources.allStringResources
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every string the module declares, read the way the window reads it.
 *
 * A `Res.string.x` accessor exists because the generator saw `x` in `values/strings.xml`, but
 * nothing checks that what it points at is *text*: a key left with an empty value, or one whose
 * `.cvr` offset is stale after a bad merge, compiles and resolves to "". The window then draws a
 * button with no label and nothing fails. Reading all of them is the check.
 */
class StringResourcesTest {

    private val strings = Res.allStringResources

    @Test
    fun `there is a string table to speak of`() {
        assertTrue(strings.size > 200, "the module ships ${strings.size} strings")
    }

    @Test
    fun `every declared string resolves to text`() = runBlocking {
        val blank = strings.filter { (_, resource) -> getString(resource).isBlank() }.keys

        assertEquals(emptySet(), blank, "keys that resolve to nothing")
    }

    @Test
    fun `a key is what its accessor is named after`() {
        val mismatched = strings.filter { (key, resource) -> resource.key != key }.keys

        assertEquals(emptySet(), mismatched)
    }

    @Test
    fun `the module carries strings and nothing else`() {
        // Drawables, fonts, plurals and arrays are all the app's; this module draws vector icons
        // from Material and takes its PDF font through CalendarHost. An entry appearing in any of
        // these is a resource added here that belongs somewhere else.
        assertEquals(emptyMap(), Res.allDrawableResources)
        assertEquals(emptyMap(), Res.allFontResources)
        assertEquals(emptyMap(), Res.allPluralStringResources)
        assertEquals(emptyMap(), Res.allStringArrayResources)
    }
}
