package org.churchpresenter.hosanna.composables.chip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.churchpresenter.hosanna.composables.foundation.HosannaStateAlpha
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.defaults.ChipDefaults
import org.churchpresenter.hosanna.composables.foundation.ChipCircleButton
import org.churchpresenter.hosanna.composables.foundation.ChipFrame
import org.churchpresenter.hosanna.composables.foundation.ChipGlyph
import org.churchpresenter.hosanna.composables.foundation.chipTextStyle
import org.churchpresenter.hosanna.composables.model.ChipColor
import org.churchpresenter.hosanna.composables.model.ChipSize
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_star
import org.churchpresenter.icons.generated.resources.ic_star_filled
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

/**
 * A chip for a file with a star to keep it as a favorite.
 *
 * @param name the file's name.
 * @param onClick called when the chip is pressed, to open the file.
 * @param favorite whether the star is filled.
 * @param onFavoriteChange called with the new state when the star is pressed.
 * @param starContentDescription the star's screen-reader name.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the chip's looks.
 * @param starInteractionSource drives the star's looks.
 * @param icon an optional leading icon, such as the file type.
 * @param open marks the file that is open now.
 * @param enabled whether it can be pressed.
 */
@Composable
fun FavoriteFileChip(
    name: String,
    onClick: () -> Unit,
    favorite: Boolean,
    onFavoriteChange: (Boolean) -> Unit,
    starContentDescription: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    starInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    icon: Painter? = null,
    open: Boolean = false,
    enabled: Boolean = true,
) {
    val metrics = ChipDefaults.Default
    ChipFrame(
        modifier = modifier,
        interactionSource = interactionSource,
        size = ChipSize.Default,
        color = ChipColor.Neutral,
        selected = open,
        enabled = enabled,
        click = Modifier.clickable(interactionSource, null, enabled, role = Role.Button, onClick = onClick),
        padding = PaddingValues(
            start = if (icon != null) metrics.iconSidePadding else metrics.padding,
            end = ChipDefaults.StarEndPadding,
        ),
    ) { colors ->
        icon?.let { ChipGlyph(it, colors.sub, ChipDefaults.Icon) }
        BasicText(
            name,
            modifier = Modifier.widthIn(max = ChipDefaults.FileNameMaxWidth),
            style = chipTextStyle(ChipSize.Default).copy(color = colors.content),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        ChipCircleButton(
            icon = painterResource(if (favorite) Res.drawable.ic_star_filled else Res.drawable.ic_star),
            glyph = ChipDefaults.StarGlyph,
            side = ChipDefaults.StarCircle,
            tint = if (favorite && enabled) ThemeColors.current.favorite else colors.sub,
            fill = colors.content.copy(alpha = HosannaStateAlpha.PRESSED),
            contentDescription = starContentDescription,
            onClick = { onFavoriteChange(!favorite) },
            interactionSource = starInteractionSource,
            enabled = enabled,
        )
    }
}
