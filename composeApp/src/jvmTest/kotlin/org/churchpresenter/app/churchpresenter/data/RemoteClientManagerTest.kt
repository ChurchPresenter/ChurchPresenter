package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which phones and tablets may drive the app without asking again.
 *
 * Every remote action normally prompts the operator, and these two lists are how that prompt is
 * skipped: a device on the allow list acts without interruption, one on the block list is refused
 * outright. So the invariant that matters is that a device is never on both — allowing something
 * previously blocked has to take it off the block list, and the other way round, or the answer
 * depends on which list happens to be consulted first.
 *
 * The lists persist to `~/.churchpresenter/remote_clients.json`, so `user.home` is swapped per test
 * and a "restart" is a second manager built against the same file.
 */
class RemoteClientManagerTest {

    private lateinit var tempHome: File
    private var realHome: String? = null

    private val clientsFile: File get() = File(tempHome, ".churchpresenter/remote_clients.json")

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-remote-clients-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    /** A second manager against the same file — i.e. the app restarted. */
    private fun restarted() = RemoteClientManager()

    // ── Starting state ──────────────────────────────────────────────────────────

    @Test
    fun `a fresh install knows nobody`() {
        val clients = RemoteClientManager()

        assertTrue(clients.allowedClients.isEmpty())
        assertTrue(clients.blockedClients.isEmpty())
        assertFalse(clients.isKnown("phone-1"), "an unrecognised device must be prompted for, not assumed")
        assertFalse(clients.isAllowed("phone-1"))
        assertFalse(clients.isBlocked("phone-1"))
    }

    // ── Allowing and blocking ───────────────────────────────────────────────────

    @Test
    fun `an allowed device acts without prompting`() {
        val clients = RemoteClientManager()

        clients.allowPermanently("phone-1")

        assertTrue(clients.isAllowed("phone-1"))
        assertTrue(clients.isKnown("phone-1"))
        assertFalse(clients.isBlocked("phone-1"))
    }

    @Test
    fun `a blocked device is refused`() {
        val clients = RemoteClientManager()

        clients.blockPermanently("phone-1")

        assertTrue(clients.isBlocked("phone-1"))
        assertTrue(clients.isKnown("phone-1"))
        assertFalse(clients.isAllowed("phone-1"))
    }

    @Test
    fun `blocking a device that was allowed takes it off the allow list`() {
        val clients = RemoteClientManager()
        clients.allowPermanently("phone-1")

        clients.blockPermanently("phone-1")

        assertFalse(
            clients.isAllowed("phone-1"),
            "a device on both lists gets whichever answer is asked for first — the block must win outright",
        )
        assertTrue(clients.isBlocked("phone-1"))
    }

    @Test
    fun `allowing a device that was blocked takes it off the block list`() {
        val clients = RemoteClientManager()
        clients.blockPermanently("phone-1")

        clients.allowPermanently("phone-1")

        assertTrue(clients.isAllowed("phone-1"))
        assertFalse(clients.isBlocked("phone-1"), "the operator changed their mind; the old decision must not linger")
    }

    @Test
    fun `deciding twice is the same as deciding once`() {
        val clients = RemoteClientManager()

        clients.allowPermanently("phone-1")
        clients.allowPermanently("phone-1")

        assertEquals(setOf("phone-1"), clients.allowedClients)
    }

    @Test
    fun `each device is decided on separately`() {
        val clients = RemoteClientManager()

        clients.allowPermanently("phone-1")
        clients.blockPermanently("tablet-2")

        assertEquals(setOf("phone-1"), clients.allowedClients)
        assertEquals(setOf("tablet-2"), clients.blockedClients)
    }

    // ── Withdrawing a decision ──────────────────────────────────────────────────

    @Test
    fun `removing an allowed device makes it unknown again`() {
        val clients = RemoteClientManager()
        clients.allowPermanently("phone-1")

        clients.removeAllowed("phone-1")

        assertFalse(clients.isKnown("phone-1"), "it should be prompted for next time, not silently refused")
        assertTrue(clients.allowedClients.isEmpty())
    }

    @Test
    fun `removing a blocked device makes it unknown again`() {
        val clients = RemoteClientManager()
        clients.blockPermanently("phone-1")

        clients.removeBlocked("phone-1")

        assertFalse(clients.isKnown("phone-1"))
    }

    @Test
    fun `removing a device that was never listed is harmless`() {
        val clients = RemoteClientManager()

        clients.removeAllowed("never-seen")
        clients.removeBlocked("never-seen")

        assertTrue(clients.allowedClients.isEmpty())
        assertTrue(clients.blockedClients.isEmpty())
    }

    // ── Blank ids ───────────────────────────────────────────────────────────────

    @Test
    fun `a device with no id is never treated as known`() {
        // A client that sends no device id must not match an empty entry that somehow got saved.
        val clients = RemoteClientManager()

        assertFalse(clients.isAllowed(""))
        assertFalse(clients.isBlocked(""))
        assertFalse(clients.isKnown(""))
    }

    @Test
    fun `a blank id cannot be added to either list`() {
        val clients = RemoteClientManager()

        clients.allowPermanently("")
        clients.blockPermanently("   ")

        assertTrue(clients.allowedClients.isEmpty(), "a blank entry would match every id-less client")
        assertTrue(clients.blockedClients.isEmpty())
    }

    // ── Names the operator gave the devices ─────────────────────────────────────

    @Test
    fun `a device has no name until one is given`() {
        assertEquals("", RemoteClientManager().getLabel("phone-1"), "an absent label reads as empty, never null")
    }

    @Test
    fun `naming a device makes it recognisable in the list`() {
        val clients = RemoteClientManager()

        clients.setLabel("phone-1", "Sound desk iPad")

        assertEquals("Sound desk iPad", clients.getLabel("phone-1"))
    }

    @Test
    fun `a name is trimmed`() {
        val clients = RemoteClientManager()
        clients.setLabel("phone-1", "  Sound desk iPad  ")
        assertEquals("Sound desk iPad", clients.getLabel("phone-1"))
    }

    @Test
    fun `clearing a name removes it rather than storing a blank`() {
        val clients = RemoteClientManager()
        clients.setLabel("phone-1", "Sound desk iPad")

        clients.setLabel("phone-1", "   ")

        assertEquals("", clients.getLabel("phone-1"))
        assertTrue(clients.clientLabels.isEmpty(), "a blank label would show as an empty row rather than the device id")
    }

    @Test
    fun `naming is independent of allowing`() {
        val clients = RemoteClientManager()

        clients.setLabel("phone-1", "Sound desk iPad")

        assertFalse(clients.isKnown("phone-1"), "giving a device a name is not the same as trusting it")
        assertEquals("Sound desk iPad", clients.getLabel("phone-1"))
    }

    @Test
    fun `a name survives the device being blocked and allowed again`() {
        val clients = RemoteClientManager()
        clients.setLabel("phone-1", "Sound desk iPad")

        clients.blockPermanently("phone-1")
        clients.allowPermanently("phone-1")

        assertEquals("Sound desk iPad", clients.getLabel("phone-1"))
    }

    // ── Names the devices gave themselves ───────────────────────────────────────

    @Test
    fun `a device that says what it is called is shown by that name`() {
        val clients = RemoteClientManager()

        clients.setReportedName("phone-1", "Pixel 7 Pro")

        assertEquals("Pixel 7 Pro", clients.getLabel("phone-1"))
    }

    @Test
    fun `a name the operator typed wins over the one the device reports`() {
        val clients = RemoteClientManager()
        clients.setReportedName("phone-1", "iPhone")

        clients.setLabel("phone-1", "Sound desk")

        assertEquals("Sound desk", clients.getLabel("phone-1"))
    }

    @Test
    fun `reconnecting does not overwrite the name the operator typed`() {
        // The whole point of typing one: the reported name was "iPhone", and there are three.
        val clients = RemoteClientManager()
        clients.setLabel("phone-1", "Sound desk")

        clients.setReportedName("phone-1", "iPhone")

        assertEquals("Sound desk", clients.getLabel("phone-1"))
    }

    @Test
    fun `clearing the typed name falls back to the reported one, not to the id`() {
        val clients = RemoteClientManager()
        clients.setReportedName("phone-1", "Pixel 7 Pro")
        clients.setLabel("phone-1", "Sound desk")

        clients.setLabel("phone-1", "")

        assertEquals("Pixel 7 Pro", clients.getLabel("phone-1"))
    }

    @Test
    fun `a reported name is trimmed, and a blank one is not stored`() {
        val clients = RemoteClientManager()

        clients.setReportedName("phone-1", "  Pixel 7 Pro  ")
        clients.setReportedName("phone-2", "   ")

        assertEquals("Pixel 7 Pro", clients.getLabel("phone-1"))
        assertEquals("", clients.getLabel("phone-2"))
    }

    @Test
    fun `an anonymous device cannot be named`() {
        val clients = RemoteClientManager()

        clients.setReportedName("", "Pixel 7 Pro")

        assertTrue(clients.reportedNames.isEmpty())
    }

    @Test
    fun `a reported name survives a restart`() {
        RemoteClientManager().setReportedName("phone-1", "Pixel 7 Pro")

        assertEquals("Pixel 7 Pro", restarted().getLabel("phone-1"))
    }

    @Test
    fun `reporting the same name again does not rewrite the file`() {
        // This runs on nearly every request a device makes; an unguarded write would be one disk
        // write per request for the whole service.
        val clients = RemoteClientManager()
        clients.setReportedName("phone-1", "Pixel 7 Pro")
        val writtenAt = clientsFile.lastModified()
        clientsFile.setLastModified(writtenAt - 5_000)

        clients.setReportedName("phone-1", "Pixel 7 Pro")

        assertEquals(writtenAt - 5_000, clientsFile.lastModified(), "the file was rewritten for an unchanged name")
    }

    @Test
    fun `a device that renames itself is shown by its new name`() {
        val clients = RemoteClientManager()
        clients.setReportedName("phone-1", "iPhone")

        clients.setReportedName("phone-1", "Sound desk iPhone")

        assertEquals("Sound desk iPhone", clients.getLabel("phone-1"))
    }

    // ── Persistence ─────────────────────────────────────────────────────────────

    @Test
    fun `decisions survive a restart`() {
        val clients = RemoteClientManager()
        clients.allowPermanently("phone-1")
        clients.blockPermanently("tablet-2")
        clients.setLabel("phone-1", "Sound desk iPad")

        val afterRestart = restarted()

        assertTrue(afterRestart.isAllowed("phone-1"), "an operator should not re-approve the same device every service")
        assertTrue(afterRestart.isBlocked("tablet-2"))
        assertEquals("Sound desk iPad", afterRestart.getLabel("phone-1"))
    }

    @Test
    fun `withdrawing a decision survives a restart`() {
        val clients = RemoteClientManager()
        clients.allowPermanently("phone-1")
        clients.removeAllowed("phone-1")

        assertFalse(restarted().isKnown("phone-1"))
    }

    @Test
    fun `reloading picks up a file changed underneath`() {
        val clients = RemoteClientManager()
        assertFalse(clients.isAllowed("phone-1"))

        RemoteClientManager().allowPermanently("phone-1") // another manager writes the file
        clients.reload()

        assertTrue(clients.isAllowed("phone-1"))
    }

    @Test
    fun `a corrupt list file starts empty rather than failing to open`() {
        clientsFile.parentFile.mkdirs()
        clientsFile.writeText("not json at all")

        val clients = RemoteClientManager()

        assertTrue(clients.allowedClients.isEmpty())
        assertTrue(clients.blockedClients.isEmpty())
        assertFalse(clients.isKnown("phone-1"), "an unreadable file must not accidentally trust anyone")
    }

    @Test
    fun `a decision made after a corrupt file repairs it`() {
        clientsFile.parentFile.mkdirs()
        clientsFile.writeText("not json at all")
        val clients = RemoteClientManager()

        clients.allowPermanently("phone-1")

        assertTrue(restarted().isAllowed("phone-1"))
    }

    @Test
    fun `a file from a newer build keeps the fields this one understands`() {
        clientsFile.parentFile.mkdirs()
        clientsFile.writeText(
            """{"allowedClients":["phone-1"],"blockedClients":[],"clientLabels":{},"futureField":true}"""
        )

        assertTrue(RemoteClientManager().isAllowed("phone-1"), "an unknown field must not cost the whole list")
    }
}
