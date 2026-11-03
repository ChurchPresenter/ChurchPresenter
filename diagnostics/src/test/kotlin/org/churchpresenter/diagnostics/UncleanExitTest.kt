package org.churchpresenter.diagnostics

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the next start makes of a run that ended under the app: the JVM's crash file and its summary. */
class UncleanExitTest {
    private val root: File = Files.createTempDirectory("unclean-exit").toFile()
    private val crashDir = File(File(System.getProperty("user.home"), ".churchpresenter"), "crash-reports")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
        crashDir.listFiles()?.filter { it.name.startsWith("hs_err_pid") }?.forEach { it.delete() }
    }

    private fun crashFile(folder: File, name: String, modifiedMs: Long, text: String = SAMPLE): File {
        folder.mkdirs()
        return File(folder, name).apply {
            writeText(text)
            setLastModified(modifiedMs)
        }
    }

    @Test
    fun `the summary says what crashed, where and on which thread`() {
        val summary = UncleanExit.summary(SAMPLE)
        assertEquals(
            "EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffb1c2d3e4f, pid=4321, tid=8765",
            summary["crash.error"],
        )
        assertEquals("C  [amdxx64.dll+0x1a2b3c]", summary["crash.problematic_frame"])
        assertEquals("""Current thread (0x000001):  JavaThread "AWT-EventQueue-0"""", summary["crash.thread"])
        assertEquals("C  [amdxx64.dll+0x1a2b3c]\nC  [opengl32.dll+0x4d5e]", summary["crash.native_frames"])
        assertEquals(
            "j  org.jetbrains.skiko.redrawer.WindowsOpenGLRedrawerKt.swapBuffers(J)V+0",
            summary["crash.java_frames"],
        )
    }

    @Test
    fun `a file that is not a crash file gives an empty summary`() {
        assertTrue(UncleanExit.summary("hello\nworld").isEmpty())
    }

    @Test
    fun `the module is the library the problematic frame names`() {
        assertEquals("amdxx64.dll", UncleanExit.module("C  [amdxx64.dll+0x1a2b3c]"))
        assertEquals("libjvm.so", UncleanExit.module("V  [libjvm.so+0x9f]  Something::method()"))
        assertNull(UncleanExit.module("j  java.lang.Object.wait()V"))
        assertNull(UncleanExit.module(null))
    }

    @Test
    fun `only a crash file written since the previous run began counts, the newest first`() {
        val since = 1_000_000_000_000L
        val work = File(root, "work")
        val temp = File(root, "temp")
        crashFile(work, "hs_err_pid1.log", since - 1)
        val newer = crashFile(temp, "hs_err_pid2.log", since + 2_000)
        crashFile(work, "hs_err_pid3.log", since + 1_000)
        crashFile(work, "replay_pid4.log", since + 5_000)
        assertEquals(newer, UncleanExit.newestCrashFile(listOf(work, temp, File(root, "missing")), since))
        assertNull(UncleanExit.newestCrashFile(listOf(work), since + 10_000))
    }

    @Test
    fun `a crash file too large to be one is not read`() {
        val file = crashFile(root, "hs_err_pid9.log", 0)
        assertEquals(SAMPLE, UncleanExit.read(file))
        file.writeBytes(ByteArray(3 * 1024 * 1024))
        assertNull(UncleanExit.read(file))
        assertNull(UncleanExit.read(File(root, "gone.log")))
    }

    @Test
    fun `the crash file found elsewhere is kept beside the app's own crash logs`() {
        val since = System.currentTimeMillis() - 60_000
        crashFile(root, "hs_err_pid77.log", since + 1_000)
        CrashReporter.reportUncleanExit(since, crashFileFolders = listOf(root))
        assertTrue(File(crashDir, "hs_err_pid77.log").isFile)
    }

    @Test
    fun `an exit that left no crash file copies nothing`() {
        CrashReporter.reportUncleanExit(System.currentTimeMillis(), crashFileFolders = listOf(root))
        assertFalse(crashDir.listFiles().orEmpty().any { it.name.startsWith("hs_err_pid") })
    }

    private companion object {
        val SAMPLE = """
            #
            # A fatal error has been detected by the Java Runtime Environment:
            #
            #  EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=0x00007ffb1c2d3e4f, pid=4321, tid=8765
            #
            # JRE version: OpenJDK Runtime Environment Temurin-21.0.12+7 (21.0.12+7) (build 21.0.12+7-LTS)
            # Java VM: OpenJDK 64-Bit Server VM Temurin-21.0.12+7 (21.0.12+7-LTS, mixed mode, tiered, g1 gc, windows-amd64)
            # Problematic frame:
            # C  [amdxx64.dll+0x1a2b3c]
            #

            ---------------  T H R E A D  ---------------

            Current thread (0x000001):  JavaThread "AWT-EventQueue-0"

            Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)
            C  [amdxx64.dll+0x1a2b3c]
            C  [opengl32.dll+0x4d5e]

            Java frames: (J=compiled Java code, j=interpreted, Vv=VM code)
            j  org.jetbrains.skiko.redrawer.WindowsOpenGLRedrawerKt.swapBuffers(J)V+0

        """.trimIndent()
    }
}
