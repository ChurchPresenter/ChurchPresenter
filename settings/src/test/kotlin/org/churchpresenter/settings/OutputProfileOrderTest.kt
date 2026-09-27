package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Moving profiles in the list. A profile that follows nothing moves with the profiles linked to it,
 * among the other such blocks; a linked profile moves only among its master's other followers.
 *
 * The list throughout: `a, b (a1, a2 follow a... placed after a), c`.
 */
class OutputProfileOrderTest {

    private val start = ProjectionSettings(
        outputProfiles = listOf(
            OutputProfile(id = "a"),
            OutputProfile(id = "a1", parentId = "a"),
            OutputProfile(id = "a2", parentId = "a"),
            OutputProfile(id = "b"),
            OutputProfile(id = "c"),
        ),
    )

    private fun ProjectionSettings.order() = outputProfiles.map { it.id }

    @Test
    fun `a master moves with its whole block`() {
        assertEquals(listOf("b", "a", "a1", "a2", "c"), start.moveOutputProfile("a", 1).order())
        assertEquals(listOf("b", "c", "a", "a1", "a2"), start.moveOutputProfileBy("a", 5).order())
    }

    @Test
    fun `a linked profile moves only among its siblings`() {
        assertEquals(listOf("a", "a2", "a1", "b", "c"), start.moveOutputProfileBy("a1", 1).order())
        // Clamped at the end of the block rather than leaving it.
        assertEquals(listOf("a", "a2", "a1", "b", "c"), start.moveOutputProfile("a1", 9).order())
    }

    @Test
    fun `moving nothing, or a profile that is not there, changes nothing`() {
        assertSame(start, start.moveOutputProfile("nope", 1))
        assertSame(start, start.moveOutputProfileBy("nope", 1))
        assertSame(start, start.moveOutputProfileBy("b", 0))
    }

    @Test
    fun `a drop lands in the nearest place among the profile's own peers`() {
        // Dropped into the middle of a's block, c lands after the whole block.
        assertEquals(listOf("a", "a1", "a2", "c", "b"), start.dropOutputProfile("c", 2).order())
        // At the very top.
        assertEquals(listOf("c", "a", "a1", "a2", "b"), start.dropOutputProfile("c", 0).order())
        // A follower dropped below the list stays the last of its siblings.
        assertEquals(listOf("a", "a2", "a1", "b", "c"), start.dropOutputProfile("a1", 5).order())
        assertSame(start, start.dropOutputProfile("nope", 0))
    }

    @Test
    fun `peers are the blocks, or a master's followers`() {
        assertEquals(listOf("a", "b", "c"), start.peersOf(start.outputProfiles[0]).map { it.id })
        assertEquals(listOf("a1", "a2"), start.peersOf(start.outputProfiles[1]).map { it.id })
    }
}
