package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import org.churchpresenter.strings.generated.resources.full_screen
import org.churchpresenter.strings.generated.resources.display_lower_third
import org.churchpresenter.strings.generated.resources.display_stage_monitor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.getBrowserSourceOutput
import org.churchpresenter.settings.getNdiOutput
import org.churchpresenter.settings.getOmtOutput
import org.churchpresenter.settings.withBrowserSourceOutput
import org.churchpresenter.settings.withNdiOutput
import org.churchpresenter.settings.withOmtOutput
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.OutputKind
import org.jetbrains.compose.resources.stringResource

/** The settings change that points output [screenIndex] of [outputKind] at profile [pickedId]. */
internal fun withPreviewProfile(
    outputKind: OutputKind,
    screenIndex: Int,
    pickedId: String,
): (AppSettings) -> AppSettings = { s ->
    val proj = s.projectionSettings
    val updatedProj = when (outputKind) {
        OutputKind.SCREEN -> proj.withAssignment(
            screenIndex, proj.getAssignment(screenIndex).copy(activeProfileId = pickedId),
        )
        OutputKind.BROWSER_SOURCE -> proj.withBrowserSourceOutput(
            screenIndex, proj.getBrowserSourceOutput(screenIndex).copy(activeProfileId = pickedId),
        )
        OutputKind.NDI -> proj.withNdiOutput(
            screenIndex, proj.getNdiOutput(screenIndex).copy(activeProfileId = pickedId),
        )
        OutputKind.OMT -> proj.withOmtOutput(
            screenIndex, proj.getOmtOutput(screenIndex).copy(activeProfileId = pickedId),
        )
    }
    s.copy(projectionSettings = updatedProj)
}

/** The chip above a preview naming its display mode -- every mode, full screen included. */
@Composable
internal fun displayModeLabel(displayMode: String): String {
    return when (displayMode) {
        Constants.DISPLAY_MODE_STAGE_MONITOR -> stringResource(Res.string.display_stage_monitor)
        // One label for both stored modes. Vertical is an orientation the app works out from the
        // output's own shape, not a mode the operator picks -- the Display Mode dropdown offers a
        // single "Lower Third" entry -- so naming it here invented a distinction the rest of the UI
        // does not have.
        Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
        Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL -> stringResource(Res.string.display_lower_third)
        // Full screen is the remaining mode, and it used to be the one with no chip at all -- so a
        // stack of previews named the two special outputs and left the ordinary ones to be guessed
        // at. It is also the row that has to be there for the header below to be clickable.
        else -> stringResource(Res.string.full_screen)
    }
}

private const val MIN_PREVIEW_CROSSFADE_MS = 100

/**
 * How long the tile fades between modes: the longer of the Bible's and the songs' transition when
 * either crossfades, at least 100ms, and no fade at all when neither does.
 */
internal fun previewCrossfadeMs(outputSettings: AppSettings): Int {
    val bible = outputSettings.bibleSettings
    val songs = outputSettings.songSettings
    if (!bible.crossfade && !songs.crossfade) return 0
    return maxOf(
        if (bible.crossfade) bible.transitionDuration.toInt() else 0,
        if (songs.crossfade) songs.transitionDuration.toInt() else 0
    ).coerceAtLeast(MIN_PREVIEW_CROSSFADE_MS)
}

/** The tile's border: red while it is live, the ordinary outline otherwise, faded between. */
@Composable
internal fun previewBorderColor(isLive: Boolean): Color {
    val borderColor by animateColorAsState(
        targetValue = if (isLive) Color.Red.copy(alpha = 0.85f)
                      else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(300),
        label = "border_color"
    )
    return borderColor
}
