package org.churchpresenter.songchords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongSectionWordsTest {

    // ── Recognising a section across languages ──────────────────────────────────

    @Test
    fun `english section words name their group`() {
        assertEquals(SongSectionWordGroup.VERSE, SongSectionWords.groupOf("Verse 1"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Chorus"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Refrain"))
        assertEquals(SongSectionWordGroup.BRIDGE, SongSectionWords.groupOf("Bridge"))
        assertEquals(SongSectionWordGroup.TAG, SongSectionWords.groupOf("Outro"))
        assertEquals(SongSectionWordGroup.INTRO, SongSectionWords.groupOf("Intro"))
    }

    @Test
    fun `a section written in its own language names the same group`() {
        // ru / uk / be — what the format already carried
        assertEquals(SongSectionWordGroup.VERSE, SongSectionWords.groupOf("Куплет 1"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Припев"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Приспів 2"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Припеў"))
        assertEquals(SongSectionWordGroup.BRIDGE, SongSectionWords.groupOf("Мост"))
        // pl / de / cs — new
        assertEquals(SongSectionWordGroup.VERSE, SongSectionWords.groupOf("Zwrotka 1"))
        assertEquals(SongSectionWordGroup.VERSE, SongSectionWords.groupOf("Strophe 2"))
        assertEquals(SongSectionWordGroup.VERSE, SongSectionWords.groupOf("Sloka"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Refren"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Kehrvers"))
        // es / pt / fr / nl
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Estribillo"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Refrão"))
        assertEquals(SongSectionWordGroup.VERSE, SongSectionWords.groupOf("Couplet 3"))
        assertEquals(SongSectionWordGroup.BRIDGE, SongSectionWords.groupOf("Puente"))
    }

    @Test
    fun `pre-chorus is not a chorus in any language`() {
        assertEquals(SongSectionWordGroup.PRE_CHORUS, SongSectionWords.groupOf("Pre-Chorus"))
        assertEquals(SongSectionWordGroup.PRE_CHORUS, SongSectionWords.groupOf("Предприпев"))
        assertFalse(SongSectionWords.isChorus("Pre-Chorus"))
        assertTrue(SongSectionWords.isChorus("Chorus"))
    }

    // ── Not mistaking a lyric for a header ──────────────────────────────────────

    @Test
    fun `a lyric that opens with a section word is not a header`() {
        // "most" is Polish and Czech for bridge, "slot" Dutch for ending, "final" Spanish —
        // short words that open real English lines. The tail rule is what keeps them apart.
        assertNull(SongSectionWords.groupOf("Most of all I love you Lord"))
        assertNull(SongSectionWords.groupOf("Refrain from evil"))
        assertNull(SongSectionWords.groupOf("Bridge over troubled water"))
        assertNull(SongSectionWords.groupOf("Tag along with me"))
        assertNull(SongSectionWords.groupOf("Version of the truth"))
        assertNull(SongSectionWords.groupOf("Final answer to the question"))
        assertNull(SongSectionWords.groupOf("Coronation day is here"))
    }

    @Test
    fun `a header may carry a number, punctuation or a repeat count`() {
        assertEquals(SongSectionWordGroup.VERSE, SongSectionWords.groupOf("Verse 2"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Припев:"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Chorus 2x"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.groupOf("Припев (2 раза)"))
        assertEquals(SongSectionWordGroup.VERSE, SongSectionWords.groupOf("  verse 1  "))
    }

    @Test
    fun `an unknown name and an empty line name nothing`() {
        assertNull(SongSectionWords.groupOf("Tacet"))
        assertNull(SongSectionWords.groupOf(""))
        assertNull(SongSectionWords.groupOf("   "))
        assertFalse(SongSectionWords.isKnownSection("Tacet"))
    }

    // ── The looser rule chord sheets are read by ────────────────────────────────

    @Test
    fun `a chord sheet heading is found wherever the word sits in a short line`() {
        assertEquals(SongSectionWordGroup.VERSE, SongSectionWords.looseGroupOf("1 куплет:"))
        assertEquals(SongSectionWordGroup.VERSE, SongSectionWords.looseGroupOf("2. Zwrotka"))
        assertEquals(SongSectionWordGroup.CHORUS, SongSectionWords.looseGroupOf("CHORUS:"))
    }

    @Test
    fun `a long line naming a section is not a chord sheet heading`() {
        assertNull(
            SongSectionWords.looseGroupOf("and then the chorus rang out across the whole room"),
        )
        assertNull(SongSectionWords.looseGroupOf(""))
    }
}
