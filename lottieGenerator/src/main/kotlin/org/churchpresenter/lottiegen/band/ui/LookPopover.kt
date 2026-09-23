package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.churchpresenter.lottiegen.band.BandColorRole
import org.churchpresenter.lottiegen.band.BandImage
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import org.churchpresenter.lottiegen.ui.components.raisedKey
import org.churchpresenter.theme.elevationPalette

private const val MAX_ALPHA = 100f
private const val MAX_BLUR_PX = 60f
private const val MAX_OFFSET_PERCENT = 100f
private const val MIN_SCALE_PERCENT = 0f
private const val MAX_SCALE_PERCENT = 400f
private const val MAX_ROTATION_DEGREES = 180f
private const val DEFAULT_SCALE_PERCENT = 100
private val POPOVER_WIDTH = 296.dp
private val POPOVER_SHAPE = RoundedCornerShape(11.dp)
private const val POPOVER_OFFSET_PX = 34

/**
 * Everything one role is drawn with beyond its colour, dropped under its row: the opacity of the
 * colour, the wash over it, the picture standing in for it and — once there is one — its blur.
 * Applied as it is dragged, so the preview answers.
 */
@Composable
internal fun LookPopover(
    viewModel: BibleLottieGenViewModel,
    role: BandColorRole,
    pickImage: (suspend () -> File?)?,
    onDismiss: () -> Unit,
) {
    val cfg = viewModel.config
    val look = cfg.look(role)
    val image = cfg.images[role]
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, POPOVER_OFFSET_PX),
        // A native chooser takes the focus; the popover waits for it rather than closing under it.
        onDismissRequest = { if (!viewModel.choosingImage) onDismiss() },
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(POPOVER_WIDTH)
                .clip(POPOVER_SHAPE)
                .background(Tokens.CardBg)
                .border(1.dp, Tokens.CardBorderOpen, POPOVER_SHAPE)
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(roleLabel(role), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Tokens.TitleText)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                HexField(
                    Strings.bandLookWash, look.washColor,
                    onColorChange = { c -> viewModel.updateLook(role) { it.copy(washColor = c) } },
                )
                Box(Modifier.weight(1f)) {
                    InlineSlider(
                        label = "",
                        value = look.washAlpha.toFloat(),
                        onValueChange = { a -> viewModel.updateLook(role) { it.copy(washAlpha = a.toInt()) } },
                        valueRange = 0f..MAX_ALPHA,
                        format = { it.toInt().toString() },
                        labelWidth = 0.dp,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PictureButton(onClick = { viewModel.chooseBandImage(role, pickImage ?: ::chooseWithSwing) })
                Text(
                    image?.name ?: Strings.bandImageNone, fontSize = 10.5.sp,
                    color = if (image != null) Tokens.OutlineText else Tokens.HintText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (image != null) {
                    Icon(
                        Icons.Default.Close, contentDescription = Strings.bandImageClear, tint = Tokens.LabelText,
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                            .clickable { viewModel.clearBandImage(role) }.padding(5.dp),
                    )
                }
            }
            if (image != null) {
                InlineSlider(
                    label = Strings.bandLookBlur,
                    value = look.blurPx.toFloat(),
                    onValueChange = { v -> viewModel.updateLook(role) { it.copy(blurPx = v.toInt()) } },
                    valueRange = 0f..MAX_BLUR_PX,
                    format = { it.toInt().toString() },
                    unit = Strings.bandUnitPx,
                )
                PictureTransformSliders(viewModel, role, image)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val okFill = elevationPalette().accent
                Box(
                    modifier = Modifier
                        .height(33.dp)
                        .raisedKey(RoundedCornerShape(17.dp), okFill, onClick = onDismiss)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(Strings.ok, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = okFill.ink)
                }
            }
        }
    }
}

/** The picture's own offset, scale and rotation, on top of its cover-fit baseline. */
@Composable
private fun PictureTransformSliders(viewModel: BibleLottieGenViewModel, role: BandColorRole, image: BandImage) {
    fun update(transform: (BandImage) -> BandImage) {
        // Reads the picture from the config passed into this lambda, not the composable's `image`
        // parameter: a fast drag can fire several onValueChange calls before recomposition catches
        // up, and basing each one on the same stale snapshot would drop whatever the others changed.
        viewModel.updateConfig { cfg -> cfg.copy(images = cfg.images + (role to transform(cfg.images[role] ?: image))) }
    }
    val isDefault = image.offsetXPercent == 0 && image.offsetYPercent == 0 &&
        image.scalePercent == DEFAULT_SCALE_PERCENT && image.rotationDegrees == 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Caption(Strings.bandLookPosition, Modifier.weight(1f))
        Icon(
            Icons.Default.RestartAlt, contentDescription = Strings.bandLookReset,
            tint = if (isDefault) Tokens.HintText else Tokens.LabelText,
            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(6.dp))
                .clickable(enabled = !isDefault) {
                    update {
                        it.copy(
                            offsetXPercent = 0, offsetYPercent = 0,
                            scalePercent = DEFAULT_SCALE_PERCENT, rotationDegrees = 0,
                        )
                    }
                }
                .padding(2.dp),
        )
    }
    InlineSlider(
        label = Strings.bandLookOffsetX,
        value = image.offsetXPercent.toFloat(),
        onValueChange = { v -> update { it.copy(offsetXPercent = v.toInt()) } },
        valueRange = -MAX_OFFSET_PERCENT..MAX_OFFSET_PERCENT,
        format = { it.toInt().toString() },
        unit = Strings.bandUnitPercent,
    )
    InlineSlider(
        label = Strings.bandLookOffsetY,
        value = image.offsetYPercent.toFloat(),
        onValueChange = { v -> update { it.copy(offsetYPercent = v.toInt()) } },
        valueRange = -MAX_OFFSET_PERCENT..MAX_OFFSET_PERCENT,
        format = { it.toInt().toString() },
        unit = Strings.bandUnitPercent,
    )
    InlineSlider(
        label = Strings.bandLookScale,
        value = image.scalePercent.toFloat(),
        onValueChange = { v -> update { it.copy(scalePercent = v.toInt()) } },
        valueRange = MIN_SCALE_PERCENT..MAX_SCALE_PERCENT,
        format = { it.toInt().toString() },
        unit = Strings.bandUnitPercent,
    )
    InlineSlider(
        label = Strings.bandLookRotation,
        value = image.rotationDegrees.toFloat(),
        onValueChange = { v -> update { it.copy(rotationDegrees = v.toInt()) } },
        valueRange = -MAX_ROTATION_DEGREES..MAX_ROTATION_DEGREES,
        format = { it.toInt().toString() },
        unit = Strings.bandUnitDegrees,
    )
}

/** The button that opens the picture chooser — the host's when it lends one, Swing's otherwise. */
@Composable
private fun PictureButton(onClick: () -> Unit) {
    val fill = elevationPalette().key
    Box(
        modifier = Modifier
            .height(30.dp)
            .raisedKey(RoundedCornerShape(8.dp), fill, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(Strings.bandImageChoose, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = fill.ink)
    }
}

/** Swing's chooser, on the event thread, for the standalone window that has no host to lend one. */
private suspend fun chooseWithSwing(): File? = withContext(Dispatchers.Swing) {
    val chooser = JFileChooser()
    chooser.fileFilter = FileNameExtensionFilter(Strings.bandImage, "png", "jpg", "jpeg", "webp")
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** A colour role: its name, its hex, the pencil to its look, and a mark when a picture stands in. */
@Composable
internal fun ColorRoleRow(viewModel: BibleLottieGenViewModel, role: BandColorRole, pickImage: (suspend () -> File?)?) {
    val cfg = viewModel.config
    var lookOpen by remember { mutableStateOf(false) }
    val hasImage = cfg.images.containsKey(role)
    Box {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HexField(roleLabel(role), cfg.colorOf(role), { c -> viewModel.updateConfig { it.withColor(role, c) } })
            // On the row rather than inside the popover below: a colour's opacity is the first
            // thing reached for on a band meant to sit over video, and behind the pencil nothing
            // on screen said it existed at all.
            Box(Modifier.weight(1f)) {
                InlineSlider(
                    label = Strings.bandLookAlpha,
                    value = cfg.alphaOf(role).toFloat(),
                    onValueChange = { a -> viewModel.updateConfig { it.withAlpha(role, a.toInt()) } },
                    valueRange = 0f..MAX_ALPHA,
                    format = { it.toInt().toString() },
                )
            }
            val lookFill = if (lookOpen) elevationPalette().accent else elevationPalette().key
            Box(
                modifier = Modifier
                    .size(FIELD_HEIGHT)
                    .raisedKey(FIELD_SHAPE, lookFill) { lookOpen = !lookOpen },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Edit, contentDescription = Strings.bandLookTooltip,
                    tint = lookFill.ink, modifier = Modifier.size(12.dp),
                )
            }
            if (hasImage) {
                Icon(
                    Icons.Default.Image, contentDescription = cfg.images[role]?.name,
                    tint = Tokens.Accent, modifier = Modifier.size(14.dp),
                )
            }
        }
        if (lookOpen) LookPopover(viewModel, role, pickImage, onDismiss = { lookOpen = false })
    }
}

