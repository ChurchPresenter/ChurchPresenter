package org.churchpresenter.slides.viewmodel

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.slides.solidImage
import org.churchpresenter.slides.tempDir
import java.awt.Color
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PicturesDropTest {

    private val dir = tempDir("cp-pictures-drop")
    private val made = mutableListOf<PicturesViewModel>()

    @AfterTest
    fun cleanUp() {
        made.forEach { runCatching { it.dispose() } }
        dir.deleteRecursively()
    }

    private fun folder(name: String): File = File(dir, name).apply {
        mkdirs()
        solidImage(this, "a.png", Color.DARK_GRAY)
        solidImage(this, "b.png", Color.GRAY)
    }

    private fun vm() = PicturesViewModel(AppSettings()).also { made += it }

    private fun awaitFolder(vm: PicturesViewModel, folder: File) {
        val deadline = System.currentTimeMillis() + 5_000
        while (vm.selectedFolder != folder || vm.images.isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "the dropped folder never opened" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `a dropped folder is the folder opened`() {
        val pics = folder("Pics")
        assertEquals(pics to null, droppedPictureFolder(listOf(File(dir, "notes.txt"), pics)))
    }

    @Test
    fun `a dropped picture opens its folder on that picture`() {
        val pics = folder("Pics")
        val b = File(pics, "b.png")
        assertEquals(pics.absoluteFile to b, droppedPictureFolder(listOf(b)))
    }

    @Test
    fun `a drop with neither a folder nor a picture opens nothing`() {
        val text = File(dir, "notes.txt").apply { parentFile.mkdirs(); writeText("x") }
        assertNull(droppedPictureFolder(listOf(text)))
        assertNull(droppedPictureFolder(emptyList()))
    }

    @Test
    fun `dropping a picture opens its folder on it and keeps the folder`() {
        val pics = folder("Pics")
        val kept = mutableListOf<String>()
        val vm = vm()
        vm.openDropped(listOf(File(pics, "b.png"))) { kept += it }
        awaitFolder(vm, pics.absoluteFile)
        val deadline = System.currentTimeMillis() + 5_000
        while (kept.isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "the folder was never kept" }
            Thread.onSpinWait()
        }
        assertEquals(listOf(pics.absolutePath), kept)
        assertEquals("b.png", vm.images[vm.selectedImageIndex].name)
    }

    @Test
    fun `a drop of nothing usable leaves the tab as it was`() {
        val vm = vm()
        runBlocking { vm.openDropped(listOf(File(dir, "missing.txt"))).join() }
        assertNull(vm.selectedFolder)
    }

    @Test
    fun `a picture name that is not a file on disk is not opened`() {
        assertNull(droppedPictureFolder(listOf(File(dir, "missing.png"))))
    }

    @Test
    fun `dropping a folder opens it at its first picture`() {
        val pics = folder("Pics")
        val vm = vm()
        vm.openDropped(listOf(pics))
        awaitFolder(vm, pics)
        assertEquals(0, vm.selectedImageIndex)
    }

    @Test
    fun `trying a folder again with none chosen does nothing`() {
        val vm = vm()
        vm.retryFolder()
        assertNull(vm.selectedFolder)
    }
}
