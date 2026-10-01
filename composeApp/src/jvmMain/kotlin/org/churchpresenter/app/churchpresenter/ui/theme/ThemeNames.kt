package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.runtime.Composable
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.custom_theme
import org.churchpresenter.strings.generated.resources.dark_theme
import org.churchpresenter.strings.generated.resources.forest_theme
import org.churchpresenter.strings.generated.resources.light_theme
import org.churchpresenter.strings.generated.resources.midnight_theme
import org.churchpresenter.strings.generated.resources.mocha_theme
import org.churchpresenter.strings.generated.resources.ocean_theme
import org.churchpresenter.strings.generated.resources.plum_theme
import org.churchpresenter.strings.generated.resources.rose_theme
import org.churchpresenter.strings.generated.resources.sand_theme
import org.churchpresenter.strings.generated.resources.slate_theme
import org.churchpresenter.strings.generated.resources.studio_theme
import org.churchpresenter.strings.generated.resources.system_theme
import org.churchpresenter.strings.generated.resources.warm_theme
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource

/**
 * What a theme is called, in one place.
 *
 * The name lived in three separate `when` blocks — the top bar's menu, the toolbar switcher and the
 * setup wizard — and the top bar did not even use one: it listed ten themes by hand. Adding the
 * eleventh, twelfth and thirteenth showed why that matters. The two `when`s failed to compile and
 * were fixed; the hand-written list compiled perfectly and simply never offered the new themes,
 * which is the failure nobody sees until a user asks where their theme went.
 *
 * Being a `when` over the enum, this cannot be added to without the compiler naming every caller.
 * Pair it with `ThemeMode.entries` — never a literal list — and a new theme appears everywhere.
 */
@Composable
fun themeDisplayName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.LIGHT -> Res.string.light_theme
        ThemeMode.DARK -> Res.string.dark_theme
        ThemeMode.SYSTEM -> Res.string.system_theme
        ThemeMode.WARM -> Res.string.warm_theme
        ThemeMode.OCEAN -> Res.string.ocean_theme
        ThemeMode.ROSE -> Res.string.rose_theme
        ThemeMode.MIDNIGHT -> Res.string.midnight_theme
        ThemeMode.FOREST -> Res.string.forest_theme
        ThemeMode.MOCHA -> Res.string.mocha_theme
        ThemeMode.STUDIO -> Res.string.studio_theme
        ThemeMode.SLATE -> Res.string.slate_theme
        ThemeMode.SAND -> Res.string.sand_theme
        ThemeMode.PLUM -> Res.string.plum_theme
        ThemeMode.CUSTOM -> Res.string.custom_theme
    }
)
