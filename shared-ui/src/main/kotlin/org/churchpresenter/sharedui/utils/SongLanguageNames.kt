package org.churchpresenter.sharedui.utils

import androidx.compose.runtime.Composable
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.languageLabel
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.song_fourth_language
import org.churchpresenter.strings.generated.resources.song_language_primary
import org.churchpresenter.strings.generated.resources.song_language_secondary
import org.churchpresenter.strings.generated.resources.song_third_language
import org.jetbrains.compose.resources.stringResource

/**
 * What language [slot] -- `0` being the primary -- is called: the name the operator gave it in the
 * song editor, or "Language N" while it has none.
 */
@Composable
fun songLanguageName(song: SongSettings, slot: Int): String =
    song.languageLabel(slot).trim().ifBlank { defaultSongLanguageName(slot) }

/** "Language N" for [slot] -- `0` being the primary -- which is what an unnamed language is called. */
@Composable
fun defaultSongLanguageName(slot: Int): String = stringResource(
    when (slot) {
        0 -> Res.string.song_language_primary
        1 -> Res.string.song_language_secondary
        2 -> Res.string.song_third_language
        else -> Res.string.song_fourth_language
    },
)
