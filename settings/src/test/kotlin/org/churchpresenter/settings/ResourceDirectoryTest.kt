package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceDirectoryTest {
    @Test
    fun `all storage sections support adding removing and promoting a directory without losing the old default`() {
        val first = File("first").absolutePath
        val second = File("second").absolutePath
        ResourceDirectory.entries.forEach { section ->
            val initial = AppSettings().withPrimaryResourceDirectory(section, first, "")
            val added = initial.withAdditionalResourceDirectory(section, second)
            assertEquals(listOf(first, second), added.resourceDirectories(section))
            assertEquals(added, added.withAdditionalResourceDirectory(section, File(second, ".").path))
            val promoted = added.withPrimaryResourceDirectory(section, second, first)
            assertEquals(listOf(second, first), promoted.resourceDirectories(section))
        }
    }

    @Test
    fun `directory lists persist while legacy single directory settings remain readable`() {
        val codec = Json { ignoreUnknownKeys = true }
        val legacy = codec.decodeFromString<AppSettings>("""{"presentationStorageDirectory":"original"}""")
        assertEquals(listOf("original"), legacy.resourceDirectories(ResourceDirectory.PRESENTATIONS))
        val updated = legacy.withAdditionalResourceDirectory(ResourceDirectory.PRESENTATIONS, "extra")
        assertEquals(updated, codec.decodeFromString<AppSettings>(codec.encodeToString(updated)))
    }
}
