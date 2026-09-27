package org.churchpresenter.app.churchpresenter.data

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceFoldersTest {
    @Test
    fun validatesCalendarDatesAndRejectsPaths() {
        assertTrue(ServiceFolders.isValidDate("29.02.2024"))
        listOf("29.02.2025", "31.04.2026", "01.01.0000", "1.01.2026", "../outside", "01/01/2026").forEach {
            assertFalse(ServiceFolders.isValidDate(it), it)
        }
    }

    @Test
    fun createsFoldersAndPreservesExistingPlanAndMaterialsOnRetry() {
        val root = Files.createTempDirectory("service-folders-test")
        try {
            val plan = ServiceFolders.create(root, "13.09.2026")
            assertFalse(Files.exists(plan))
            listOf("Pictures", "Presentations", "Media").forEach {
                assertTrue(Files.isDirectory(plan.parent.resolve(it)))
                assertTrue(Files.isRegularFile(plan.parent.resolve(it).resolve(".gitkeep")))
            }
            Files.writeString(plan, "existing plan")
            val picture = plan.parent.resolve("Pictures/photo.png")
            Files.writeString(picture, "existing picture")
            assertEquals(plan, ServiceFolders.create(root, "13.09.2026"))
            assertEquals("existing plan", Files.readString(plan))
            assertEquals("existing picture", Files.readString(picture))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun invalidDateMakesNoDirectories() {
        val root = Files.createTempDirectory("service-folders-test")
        try {
            assertFailsWith<IllegalArgumentException> { ServiceFolders.create(root, "31.02.2026") }
            Files.list(root).use { assertEquals(0L, it.count()) }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun fileConflictIsReportedWithoutOverwritingTheFile() {
        val root = Files.createTempDirectory("service-folders-test")
        try {
            val service = Files.createDirectory(root.resolve("13.09.2026"))
            val conflict = service.resolve("Pictures")
            Files.writeString(conflict, "keep me")
            assertFailsWith<FileAlreadyExistsException> { ServiceFolders.create(root, "13.09.2026") }
            assertEquals("keep me", Files.readString(conflict))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
