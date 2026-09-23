package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which of the six backgrounds a profile carries itself, and which it takes from the Background tab.
 *
 * The choice is per surface rather than per profile, so the interesting cases are all the same
 * shape: one surface overridden must move and the other five must not. A leak in either direction
 * is silent -- the house background quietly changing when one output is customized, or a profile's
 * own band staying put when the house colour is changed for everyone.
 */
class BackgroundSurfaceTest {

    private val house = BackgroundSettings(
        defaultBackgroundDim = 10,
        defaultLowerThirdBackgroundDim = 11,
        bibleBackground = BackgroundConfig(backgroundColor = "#111111"),
        bibleLowerThirdBackground = BackgroundConfig(backgroundColor = "#222222"),
        songBackground = BackgroundConfig(backgroundColor = "#333333"),
        songLowerThirdBackground = BackgroundConfig(backgroundColor = "#444444"),
    )

    private val mine = BackgroundSettings(
        defaultBackgroundDim = 90,
        defaultLowerThirdBackgroundDim = 91,
        bibleBackground = BackgroundConfig(backgroundColor = "#AAAAAA"),
        bibleLowerThirdBackground = BackgroundConfig(backgroundColor = "#BBBBBB"),
        songBackground = BackgroundConfig(backgroundColor = "#CCCCCC"),
        songLowerThirdBackground = BackgroundConfig(backgroundColor = "#DDDDDD"),
    )

    private fun resolved(vararg overridden: BackgroundSurface) =
        resolveBackgroundSurfaces(house, mine, overridden.map { it.name }.toSet())

    @Test
    fun `a profile overriding nothing is the house background exactly`() {
        assertEquals(house, resolved())
    }

    @Test
    fun `a profile overriding everything is its own background exactly`() {
        assertEquals(mine, resolved(*BackgroundSurface.entries.toTypedArray()))
    }

    @Test
    fun `each surface moves on its own and leaves the other five alone`() {
        val cases = listOf<Pair<BackgroundSurface, (BackgroundSettings) -> Any>>(
            BackgroundSurface.DEFAULT to { it.defaultBackgroundDim },
            BackgroundSurface.DEFAULT_LOWER_THIRD to { it.defaultLowerThirdBackgroundDim },
            BackgroundSurface.BIBLE to { it.bibleBackground },
            BackgroundSurface.BIBLE_LOWER_THIRD to { it.bibleLowerThirdBackground },
            BackgroundSurface.SONG to { it.songBackground },
            BackgroundSurface.SONG_LOWER_THIRD to { it.songLowerThirdBackground },
        )

        for ((surface, read) in cases) {
            val out = resolved(surface)
            assertEquals(read(mine), read(out), "$surface was overridden and must be the profile's")
            for ((other, readOther) in cases) {
                if (other == surface) continue
                assertEquals(readOther(house), readOther(out), "$other must still follow the document")
            }
        }
    }

    @Test
    fun `a surface name the build no longer has is ignored`() {
        // Stored as names, so a document written by a build with a surface this one lacks still
        // resolves -- as "follow" for the surface it cannot place, which is the safe direction.
        assertEquals(house, resolveBackgroundSurfaces(house, mine, setOf("SOME_FUTURE_SURFACE")))
    }
}
