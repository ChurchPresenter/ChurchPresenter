package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [Constants] holds the string values that settings *defaults* are written with — a display mode,
 * an alignment, a background type — so a typo in one is a settings file that decodes to a value
 * nothing in the app matches. These pin the ones a default names, and the two ports, which must
 * stay distinct because both are bound on loopback at startup.
 */
class ConstantsTest {

    @Test
    fun `the two fixed loopback ports do not collide`() {
        assertNotEquals(Constants.SINGLE_INSTANCE_PORT, Constants.PLANNING_CENTER_OAUTH_PORT)
        assertTrue(Constants.SINGLE_INSTANCE_PORT > 1023, "must be outside the privileged range")
        assertTrue(Constants.PLANNING_CENTER_OAUTH_PORT > 1023, "must be outside the privileged range")
    }

    @Test
    fun `the PCO redirect port is the one the registered redirect URI names`() {
        // Changing this breaks every user's Planning Center app registration, which spells the port out.
        assertEquals(47850, Constants.PLANNING_CENTER_OAUTH_PORT)
    }

    @Test
    fun `alignment and position names are distinct`() {
        val positions = listOf(
            Constants.TOP_LEFT, Constants.TOP_CENTER, Constants.TOP_RIGHT,
            Constants.CENTER_LEFT, Constants.CENTER, Constants.CENTER_RIGHT,
            Constants.BOTTOM_LEFT, Constants.BOTTOM_CENTER, Constants.BOTTOM_RIGHT,
        )

        assertEquals(positions.size, positions.toSet().size, "two positions collapsed onto one string")
    }

    @Test
    fun `a default screen assignment is spelled with the values its own accessors compare against`() {
        // ScreenAssignment.displayMode's default is the literal "fullscreen" rather than the
        // constant, and isLowerThird reads the constants -- so the two have to agree by value.
        val assignment = ScreenAssignment()

        assertEquals(Constants.DISPLAY_MODE_FULLSCREEN, assignment.displayMode)
        assertFalse(assignment.isLowerThird)
        assertTrue(assignment.showBible, "the default bible mode must not read as off")
        assertEquals(Constants.OUTPUT_ROLE_NORMAL, assignment.primaryOutputRole)
    }

    @Test
    fun `the nested groups are reachable and their keys are the platform's own spellings`() {
        assertEquals("os.name", Constants.SystemProperties.OS_NAME)
        assertEquals("user.home", Constants.SystemProperties.USER_HOME)
        assertEquals("org.freedesktop.portal.Desktop", Constants.DBus.DESKTOP_OBJECT_NAME)
        assertEquals("current_folder", Constants.DBus.Options.CURRENT_FOLDER)
    }
}
