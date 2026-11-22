package org.churchpresenter.app.churchpresenter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstallCheckTest {
    private fun path(vararg entries: String) = entries.joinToString(File.pathSeparator)

    @Test
    fun `a jar that is not on disk is missing, and one that is is not`() {
        val there = setOf("app/theme.jar", "app/settings.jar")

        val missing = missingClassPathEntries(
            path("app/theme.jar", "app/strings.jar", "app/settings.jar"),
            exists = { it.path.replace(File.separatorChar, '/') in there },
        )

        assertEquals(listOf("strings.jar"), missing.map { it.name })
    }

    @Test
    fun `blank and wildcard entries are never reported`() {
        assertTrue(missingClassPathEntries(path("", "app/*", " "), exists = { false }).isEmpty())
    }

    @Test
    fun `Sentry is told the file names only`() {
        val tags = damagedInstallTags(listOf(File("C:/Users/Pastor John/app/strings.jar"), File("C:/x/jcef.jar")))

        assertEquals("2", tags["install.missing_count"])
        assertEquals("strings.jar,jcef.jar", tags["install.missing"])
    }

    @Test
    fun `the message comes from the strings`() {
        assertEquals(DAMAGED_INSTALL_FALLBACK, damagedInstallMessage())
    }

    @Test
    fun `the message is still said in English when the strings jar is the one missing`() {
        val message = damagedInstallMessage {
            throw NoClassDefFoundError("org/churchpresenter/strings/generated/resources/Res\$string")
        }

        assertEquals(DAMAGED_INSTALL_FALLBACK, message)
    }

    @Test
    fun `reporting a damaged install shows the message`() {
        val shown = mutableListOf<String>()

        reportDamagedInstall(listOf(File("strings.jar")), show = { shown += it })

        assertEquals(listOf(DAMAGED_INSTALL_FALLBACK), shown)
    }
}
