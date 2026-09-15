package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.lottiegen.band.BandStyle
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens

private val THUMB_WIDTH = 104.dp
private val THUMB_MIN_HEIGHT = 20.dp
private val THUMB_SHAPE = RoundedCornerShape(4.dp)
private val TEMPLATE_ROW_PADDING = 6.dp

/**
 * The style, chosen from a list that shows each one as the band it makes — generated in the
 * current colours, at the band's own aspect, and seen at its hold frame.
 */
@Composable
internal fun TemplatePicker(viewModel: BibleLottieGenViewModel, onPick: (BandStyle) -> Unit) {
    val cfg = viewModel.config
    var open by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val count = cfg.roles().size
    val aspect = cfg.canvasW.toFloat() / cfg.canvasH.toFloat()
    val thumbHeight = (THUMB_WIDTH / aspect).coerceAtLeast(THUMB_MIN_HEIGHT)
    val rowHeight = thumbHeight + TEMPLATE_ROW_PADDING * 2
    // The thumbnails are asked for as soon as the field shows, so the menu opens with them ready.
    LaunchedEffect(cfg) { viewModel.ensureStyleThumbnails() }
    Box(Modifier.onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }) {
        CaptionedButton(
            caption = Strings.bandTemplate,
            value = Strings.bandEnumLabel("style", cfg.bandStyle.name),
            open = open,
            onClick = { open = !open },
            leading = { StyleThumbnail(viewModel, cfg.bandStyle, thumbHeight) },
            trailing = { CountBadge(Strings.bandTemplateColors(count)) },
        )
        if (open) {
            PopupMenu(
                onDismiss = { open = false },
                width = anchorWidth,
                keys = MenuKeys(BandStyle.entries.indexOf(cfg.bandStyle), BandStyle.entries.size, rowHeight) {
                    onPick(BandStyle.entries[it])
                },
            ) {
                BandStyle.entries.forEach { style ->
                    TemplateRow(viewModel, style, thumbHeight, selected = style == cfg.bandStyle) {
                        onPick(style)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(
    viewModel: BibleLottieGenViewModel,
    style: BandStyle,
    thumbHeight: Dp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val count = 2 + (if (style.usesSecond) 1 else 0) + (if (style.usesTertiary) 1 else 0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumbHeight + TEMPLATE_ROW_PADDING * 2)
            .clip(FIELD_SHAPE)
            .background(menuRowBackground(selected))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = TEMPLATE_ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        StyleThumbnail(viewModel, style, thumbHeight)
        Text(
            Strings.bandEnumLabel("style", style.name), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) Tokens.Accent else Tokens.PrimaryText, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            Strings.bandTemplateColors(count), fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
            color = Tokens.LabelText,
        )
        SelectedMark(selected)
    }
}

@Composable
private fun CountBadge(text: String) {
    Text(
        text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Tokens.Accent, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Tokens.LogoChipBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** The band a style makes, in the current colours, at its hold frame; a blank chip until it is built. */
@Composable
private fun StyleThumbnail(viewModel: BibleLottieGenViewModel, style: BandStyle, height: Dp) {
    val composition = viewModel.styleThumbnails[style]
    Box(
        Modifier
            .size(THUMB_WIDTH, height)
            .clip(THUMB_SHAPE)
            .background(Tokens.CanvasBg)
            .border(1.dp, Tokens.FieldBorder, THUMB_SHAPE),
    ) {
        if (composition != null) {
            val timeline = viewModel.thumbnailTimeline
            val hold = timeline.holdStart.toFloat() / timeline.totalFrames
            Image(
                painter = rememberLottiePainter(composition = composition, progress = { hold }),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                // Tagged once it draws, so a test can wait for the chip rather than guess.
                modifier = Modifier.fillMaxSize().testTag(STYLE_THUMBNAIL_TAG),
            )
        }
    }
}

/** On every drawn style thumbnail; the screenshot suite waits for it. */
const val STYLE_THUMBNAIL_TAG = "bandStyleThumbnail"
