package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisposedOwnerRaceTest {

    private val race = IllegalArgumentException("RootNodeOwner is already disposed")

    @Test
    fun `only Compose's disposed-owner IllegalArgumentException is the race`() {
        assertTrue(isDisposedOwnerRace(race))
        assertFalse(isDisposedOwnerRace(IllegalArgumentException("Failed requirement.")))
        assertFalse(isDisposedOwnerRace(IllegalArgumentException()))
        assertFalse(isDisposedOwnerRace(IllegalArgumentException("RootNodeOwner is already disposed!")))
        assertFalse(
            isDisposedOwnerRace(IllegalStateException("RootNodeOwner is already disposed")),
            "the same words from another type are not this race",
        )
        assertFalse(isDisposedOwnerRace(RuntimeException(race)), "nor is something that merely wraps it")
    }

    @Test
    fun `the race is dropped, and everything else goes to the usual handler`() {
        val usual = mutableListOf<Throwable>()
        val other = IllegalArgumentException("Failed requirement.")
        val error = OutOfMemoryError("x")

        onWindowException(race) { usual += it }
        onWindowException(other) { usual += it }
        onWindowException(error) { usual += it }

        assertEquals(listOf<Throwable>(other, error), usual)
    }

    @Test
    fun `what the usual handler throws reaches the caller`() {
        val other = IllegalStateException("boom")

        val thrown = runCatching { onWindowException(other) { throw it } }.exceptionOrNull()

        assertEquals(other, thrown, "the rethrow to the uncaught handler still happens")
    }
}
