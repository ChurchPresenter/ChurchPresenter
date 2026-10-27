package org.churchpresenter.appsettings

import androidx.compose.runtime.Composable
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.offscreen_web
import org.churchpresenter.strings.generated.resources.offscreen_web_hint
import org.jetbrains.compose.resources.stringResource

/** Smooth web output: the live website rendered once off screen for every output; read at start-up. */
@Composable
internal fun OffscreenWebRow(settings: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    GeneralToggleRow(
        label = Res.string.offscreen_web,
        hint = stringResource(Res.string.offscreen_web_hint),
        checked = settings.projectionSettings.offscreenWebEnabled,
        onCheckedChange = { on ->
            onSettingsChange { s -> s.copy(projectionSettings = s.projectionSettings.copy(offscreenWebEnabled = on)) }
        }
    )
}
