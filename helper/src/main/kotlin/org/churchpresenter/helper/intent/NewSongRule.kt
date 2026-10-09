package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.HelperAction

private val NEW_SONG = listOf("new song", "add a song", "add song", "add a new song", "create a song", "write a song")

/** "New song", said outright rather than asked: the New Song button, as the question gets. */
internal fun newSongRule(r: Request): Resolution? =
    if ((r.hasPhrase(NEW_SONG) || addsASong(r)) && !r.says("schedule")) {
        act(HelperAction.Highlight(NavigationTopics.newSong()))
    } else {
        null
    }

private val ADDING_A_SONG = setOf("where", "how", "do", "i", "can", "a", "the", "new", "add", "song")

/** "Song where add", as "गीत कहाँ जोड़ूँ" reads: those words in any order, and nothing else the rules read. */
private fun addsASong(r: Request): Boolean =
    r.says("add") && r.says("song") && r.words.all { word -> word in ADDING_A_SONG || word.none { it in 'a'..'z' } }
