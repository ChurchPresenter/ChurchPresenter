package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import org.churchpresenter.app.churchpresenter.data.Language

val LocalLanguage = staticCompositionLocalOf { Language.ENGLISH }

@Composable
fun LanguageProvider(
    language: Language,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLanguage provides language) {
        content()
    }
}

