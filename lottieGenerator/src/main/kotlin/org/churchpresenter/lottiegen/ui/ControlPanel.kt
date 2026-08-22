package org.churchpresenter.lottiegen.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.LottieGenState
import org.churchpresenter.lottiegen.ui.components.AccentButton
import org.churchpresenter.lottiegen.ui.components.LottieTextField
import org.churchpresenter.lottiegen.ui.components.SubtleButton

/** The ranges every numeric field in this panel is clamped to. */
internal const val MIN_CANVAS_PX = 100
internal const val MAX_CANVAS_W_PX = 7680
internal const val MAX_CANVAS_H_PX = 4320
internal const val MIN_BASE_SIZE = 10
internal const val MAX_BASE_SIZE = 80
internal const val MIN_TEXT_EM = 0.5f
internal const val MAX_TEXT_EM = 4f
internal const val MIN_ANIM_SECONDS = 0.5f
internal const val MAX_ANIM_SECONDS = 20f
internal const val MAX_HOLD_SECONDS = 30f
internal const val MIN_NUDGE_EM = -0.5f
internal const val MAX_NUDGE_EM = 1f


/** Two equal columns, the layout every field pair in the panel uses. */
@Composable
internal fun FieldRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        content()
    }
}

/** The app mark in the panel header. */
@Composable
private fun PanelHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Tokens.HeaderHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Tokens.LogoChipBg),
            contentAlignment = Alignment.Center
        ) {
            // A lower third in miniature: a frame with a caption bar across its lower edge.
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 11.dp)
                    .border(1.3.dp, Tokens.LogoIcon, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.BottomStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp, bottom = 1.5.dp)
                        .size(width = 8.dp, height = 2.6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Tokens.LogoIcon)
                )
            }
        }
        Text(
            Strings.appTitle,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.21).sp,
            color = Tokens.TitleText,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanel(viewModel: LottieGenState, panelWidth: Dp = 436.dp) {
    val scrollState = rememberScrollState()
    var showBatchImport by remember { mutableStateOf(false) }
    var batchImportText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(panelWidth)
            .background(Tokens.PanelBg)
    ) {
        PanelHeader()
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.CardBorder))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(start = 13.dp, end = 13.dp, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {

            CanvasSection(viewModel)
            StyleLayoutSection(viewModel)
            TextSection(viewModel)
            TextStyleSection(viewModel)
            ColorsSection(viewModel)
            ShapeSection(viewModel)
            LogoSection(viewModel)
            TimingSection(viewModel)
            PositionSection(viewModel)
            ActionsSection(viewModel)
            LibrarySection(viewModel, onBatchImport = { showBatchImport = true })
        }
    }

    if (showBatchImport) {
        AlertDialog(
            onDismissRequest = { showBatchImport = false; batchImportText = "" },
            containerColor = Tokens.CardBg,
            title = { Text(
                Strings.batchImportTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.TitleText,
            ) },
            text = {
                Column {
                    Text(Strings.batchImportHint, fontSize = 12.sp, color = Tokens.LabelText)
                    Spacer(Modifier.height(8.dp))
                    LottieTextField(
                        value = batchImportText,
                        onValueChange = { batchImportText = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        singleLine = false,
                        fillWidth = true,
                        placeholder = { Text(Strings.batchImportPlaceholder, fontSize = 12.sp) }
                    )
                }
            },
            confirmButton = {
                AccentButton(Strings.batchImportBtn, {
                    val (added, updated) = viewModel.batchImportPresets(batchImportText)
                    viewModel.updateStatusText(Strings.batchImportedStatus(added, updated))
                    showBatchImport = false
                    batchImportText = ""
                })
            },
            dismissButton = {
                SubtleButton(Strings.cancelBtn, { showBatchImport = false; batchImportText = "" })
            }
        )
    }
}
