package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.Language
import kotlin.test.Test
import kotlin.test.assertEquals

/** Split out of `ThemeRenderTest` when the theme moved to :theme; `LanguageProvider` stayed here. */
@OptIn(ExperimentalTestApi::class)
class LanguageProviderTest {

    @Test
    fun `the language provider hands its language down`() {
        var seen: Language? = null
        runComposeUiTest {
            setContent { LanguageProvider(Language.RUSSIAN) { seen = LocalLanguage.current } }
        }

        assertEquals(Language.RUSSIAN, seen)
    }

    @Test
    fun `the language provider can be nested for one part of the screen`() {
        // A presenter window can run in a different language from the operator's own UI.
        var outer: Language? = null
        var inner: Language? = null
        runComposeUiTest {
            setContent {
                LanguageProvider(Language.ENGLISH) {
                    outer = LocalLanguage.current
                    LanguageProvider(Language.RUSSIAN) { inner = LocalLanguage.current }
                }
            }
        }

        assertEquals(Language.ENGLISH, outer)
        assertEquals(Language.RUSSIAN, inner, "the inner scope must win inside itself")
    }

    @Test
    fun `english is what is read when nothing has provided a language`() {
        var seen: Language? = null
        runComposeUiTest {
            setContent { seen = LocalLanguage.current }
        }

        assertEquals(Language.ENGLISH, seen, "a missing provider must not leave the UI blank or throw")
    }
}
