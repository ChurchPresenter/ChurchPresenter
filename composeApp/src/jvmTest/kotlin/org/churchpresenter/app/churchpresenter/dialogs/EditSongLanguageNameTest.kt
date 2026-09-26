@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** The song editor's install-wide language-name field. */
class EditSongLanguageNameTest {

    @Test
    fun `a language name typed in the editor is stored on Save`() {
        val stored = mutableListOf<List<String>>()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    // As SongsTab passes them: rebuilt on every recomposition from the install's names.
                    var names by remember { mutableStateOf(emptyList<String>()) }
                    EditSongContent(
                        song = SongItem(number = "1", title = "Song", songbook = "Hymnal", lyrics = listOf("a")),
                        songbooks = emptyList(),
                        existingSongs = emptyList(),
                        isNewSong = false,
                        theme = ThemeMode.LIGHT,
                        languageNames = List(4) { names.getOrElse(it) { "" } },
                        onLanguageNamesChange = { names = it; stored += it },
                        onDismiss = {},
                        onSave = { _, _ -> },
                    )
                }
            }

            onNodeWithTag(LANGUAGE_NAME_FIELD_TAG).performClick()
            onNodeWithTag(LANGUAGE_NAME_FIELD_TAG).assertIsFocused()
            onNodeWithTag(LANGUAGE_NAME_FIELD_TAG).performTextInput("Yoruba")
            onNodeWithTag(LANGUAGE_NAME_FIELD_TAG).assertTextContains("Yoruba")

            onNodeWithText("Save").performClick()
            waitForIdle()

            assertEquals(listOf(listOf("Yoruba", "", "", "")), stored)
        }
    }

    @Test
    fun `a click anywhere on the card puts the cursor in the field`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                EditSongContent(
                    song = SongItem(number = "1", title = "Song", songbook = "Hymnal", lyrics = listOf("a")),
                    songbooks = emptyList(), existingSongs = emptyList(), isNewSong = false,
                    theme = ThemeMode.LIGHT, languageNames = emptyList(), onLanguageNamesChange = {},
                    onDismiss = {}, onSave = { _, _ -> },
                )
            }
        }

        // The caption, above the line of text the field itself occupies.
        onNodeWithText("LANGUAGE NAME", ignoreCase = true).performClick()
        waitForIdle()

        onNodeWithTag(LANGUAGE_NAME_FIELD_TAG).assertIsFocused()
    }
}
