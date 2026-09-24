package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.output_scale_mode
import churchpresenter.composeapp.generated.resources.output_scale_mode_mixed
import org.churchpresenter.settings.OutputScaleMode
import org.jetbrains.compose.resources.stringResource

/**
 * The Pictures and Media tabs' scale button's tooltip: the mode every profile shares, or -- while
 * they differ -- that they do, and what one press will set them all to.
 */
@Composable
internal fun scaleButtonLabel(shared: OutputScaleMode?, current: OutputScaleMode): String =
    if (shared == null) stringResource(Res.string.output_scale_mode_mixed, stringResource(current.label))
    else stringResource(Res.string.output_scale_mode, stringResource(shared.label))
