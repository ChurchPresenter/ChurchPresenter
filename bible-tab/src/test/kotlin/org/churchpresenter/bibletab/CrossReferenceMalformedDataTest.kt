package org.churchpresenter.bibletab

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrossReferenceMalformedDataTest {

    private fun repository(refs: String) = CrossReferenceRepository { """{"v":1,"r":{$refs}}""".toByteArray() }

    @Test
    fun `source keys that are not nine digits are dropped`() = runTest {
        val repo = repository(""""43003016":"045005008","04300301x":"045005008","043003016":"045005008"""")
        repo.ensureLoaded()

        assertEquals(listOf(CrossRef(45, 5, 8)), repo.forVerse(43, 3, 16))
    }

    @Test
    fun `targets that are not references are dropped and a bad range end is ignored`() = runTest {
        val repo = repository(""""043003016":"bogus 04500500x 045005008-x 019033006-009"""")
        repo.ensureLoaded()

        assertEquals(listOf(CrossRef(45, 5, 8), CrossRef(19, 33, 6, 9)), repo.forVerse(43, 3, 16))
    }

    @Test
    fun `a reference with any part out of range has none`() = runTest {
        val repo = repository(""""043003016":"045005008"""")
        repo.ensureLoaded()

        listOf(Triple(0, 3, 16), Triple(1000, 3, 16), Triple(43, 0, 16), Triple(43, 1000, 16),
            Triple(43, 3, 0), Triple(43, 3, 1000)).forEach { (b, c, v) ->
            assertTrue(repo.forVerse(b, c, v).isEmpty(), "$b $c:$v")
        }
    }
}
