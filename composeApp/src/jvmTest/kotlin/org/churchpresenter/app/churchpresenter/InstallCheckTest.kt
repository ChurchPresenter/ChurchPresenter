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

        val missing = missingClassPathJars(
            path("app/theme.jar", "app/strings.jar", "app/settings.jar"),
            exists = { it.path.replace(File.separatorChar, '/') in there },
        )

        assertEquals(listOf("strings.jar"), missing.map { it.name })
    }

    @Test
    fun `folders, wildcards and blanks are never reported`() {
        val missing = missingClassPathJars(
            path("", "app/*", "build/processedResources/jvm/main", "build/classes/kotlin/jvm/main"),
            exists = { false },
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun `Sentry is told the file names only`() {
        val tags = damagedInstallTags(listOf(File("C:/Users/Pastor John/app/strings.jar"), File("C:/x/jcef.jar")))

        assertEquals("2", tags["install.missing_count"])
        assertEquals("strings.jar,jcef.jar", tags["install.missing"])
    }

    @Test
    fun `a damaged install is reported and the start carries on`() {
        reportIfInstallDamaged(listOf(File("strings.jar")))
        reportIfInstallDamaged(emptyList())
    }
}
