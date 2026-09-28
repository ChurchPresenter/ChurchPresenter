package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateInstallerCleanupTest {

    private fun withTempDir(block: (File) -> Unit) {
        val dir = createTempDirectory("update-cleanup").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `installers an earlier update left behind are deleted`() = withTempDir { dir ->
        val left = listOf(".msi", ".dmg", ".bin").map { suffix ->
            File.createTempFile(UPDATE_INSTALLER_PREFIX, suffix, dir)
        }

        assertEquals(3, deleteLeftoverUpdateInstallers(dir))
        assertTrue(left.none { it.exists() }, "every leftover installer must be gone")
    }

    @Test
    fun `everything else in the temp directory is left alone`() = withTempDir { dir ->
        val unrelated = File(dir, "someone-else.msi").apply { writeText("x") }
        val nearMiss = File(dir, "ChurchPresenter-settings.json").apply { writeText("{}") }
        val folder = File(dir, "$UPDATE_INSTALLER_PREFIX-folder").apply { mkdir() }

        assertEquals(0, deleteLeftoverUpdateInstallers(dir))
        assertTrue(unrelated.exists(), "another program's file is not ours to delete")
        assertTrue(nearMiss.exists(), "a ChurchPresenter file that is not an installer stays")
        assertTrue(folder.isDirectory, "only files are deleted, never a directory")
    }

    @Test
    fun `a temp directory that does not exist deletes nothing and does not throw`() = withTempDir { dir ->
        assertEquals(0, deleteLeftoverUpdateInstallers(File(dir, "missing")))
    }
}
