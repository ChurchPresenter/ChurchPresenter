package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.dark_theme
import churchpresenter.composeapp.generated.resources.forest_theme
import churchpresenter.composeapp.generated.resources.light_theme
import churchpresenter.composeapp.generated.resources.midnight_theme
import churchpresenter.composeapp.generated.resources.mocha_theme
import churchpresenter.composeapp.generated.resources.ocean_theme
import churchpresenter.composeapp.generated.resources.plum_theme
import churchpresenter.composeapp.generated.resources.rose_theme
import churchpresenter.composeapp.generated.resources.sand_theme
import churchpresenter.composeapp.generated.resources.slate_theme
import churchpresenter.composeapp.generated.resources.studio_theme
import churchpresenter.composeapp.generated.resources.system_theme
import churchpresenter.composeapp.generated.resources.warm_theme
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
    }
)
