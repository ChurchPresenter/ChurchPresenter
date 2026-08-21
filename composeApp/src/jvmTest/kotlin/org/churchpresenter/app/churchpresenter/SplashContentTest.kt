package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SplashContentTest {

    @Test
    fun `the splash names the app and says it is loading`() = runComposeUiTest {
        setContent { SplashContent(ThemeMode.SYSTEM) }

        onNodeWithText("Church Presenter").assertIsDisplayed()
        onNodeWithText("Loading...").assertIsDisplayed()
    }

    @Test
    fun `the splash draws the same in every theme`() = runComposeUiTest {
        ThemeMode.entries.forEach { theme ->
            setContent { SplashContent(theme) }
            onNodeWithText("Church Presenter").assertIsDisplayed()
        }
    }
}
