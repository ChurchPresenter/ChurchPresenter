package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_scale_fill
import churchpresenter.composeapp.generated.resources.canvas_scale_fit
import churchpresenter.composeapp.generated.resources.canvas_scale_stretch
import org.churchpresenter.settings.OutputScaleMode
import org.jetbrains.compose.resources.StringResource

/** How the Pictures and Media outputs draw in this mode. */
internal val OutputScaleMode.contentScale: ContentScale
    get() = when (this) {
        OutputScaleMode.FIT -> ContentScale.Fit
        OutputScaleMode.FILL -> ContentScale.Crop
        OutputScaleMode.STRETCH -> ContentScale.FillBounds
    }

/** The mode's name, shared with a Canvas source's scale dropdown. */
internal val OutputScaleMode.label: StringResource
    get() = when (this) {
        OutputScaleMode.FIT -> Res.string.canvas_scale_fit
        OutputScaleMode.FILL -> Res.string.canvas_scale_fill
        OutputScaleMode.STRETCH -> Res.string.canvas_scale_stretch
    }

/** The scale button's icon, so the mode reads at a glance without hovering. */
internal val OutputScaleMode.icon: ImageVector
    get() = when (this) {
        OutputScaleMode.FIT -> Icons.Filled.FitScreen
        OutputScaleMode.FILL -> Icons.Filled.Crop
        OutputScaleMode.STRETCH -> Icons.Filled.AspectRatio
    }
