package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.churchpresenter.app.churchpresenter.data.Language

val LocalLanguage = staticCompositionLocalOf { Language.ENGLISH }

/**
 * The layout direction [language] is written in — right to left for Arabic and Persian.
 *
 * Compose mirrors rows, alignments and padding off [LocalLayoutDirection], so this is what turns a
 * translated interface into one that actually reads correctly rather than one with the sidebar on
 * the wrong side.
 */
fun layoutDirectionFor(language: Language): LayoutDirection =
    if (language.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr

@Composable
fun LanguageProvider(
    language: Language,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalLanguage provides language,
        LocalLayoutDirection provides layoutDirectionFor(language)
    ) {
        content()
    }
}
