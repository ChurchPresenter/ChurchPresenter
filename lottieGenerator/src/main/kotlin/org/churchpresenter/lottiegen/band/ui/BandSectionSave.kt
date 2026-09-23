package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens
import org.churchpresenter.theme.components.SettingsTextField

/** The Save pane: the file name, what is about to be written, and the button that writes it. */
@Composable
internal fun SaveSection(viewModel: BibleLottieGenViewModel) {
    val cfg = viewModel.config
    SettingsTextField(
        value = viewModel.fileName,
        onValueChange = viewModel::updateFileName,
        label = Strings.bandFileName,
        fillWidth = true,
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        SummaryRow(
            Strings.bandSummaryTemplate,
            Strings.bandEnumLabel("style", cfg.bandStyle.name) + " · " + Strings.bandTemplateColors(cfg.roles().size),
        )
        SummaryRow(
            Strings.bandSummaryMotion,
            Strings.bandEnumLabel("entrance", cfg.entrance.name) + " · " +
                Strings.bandEnumLabel("text", cfg.textAnimation.name),
        )
        SummaryRow(
            Strings.bandSummaryDuration,
            "%.1f".format(viewModel.timeline.totalSeconds) + Strings.bandUnitSeconds,
        )
        SummaryRow(Strings.bandSummaryLayout, Strings.bandEnumLabel("layout", cfg.layout.name))
        SummaryRow(
            Strings.bandSummaryType,
            "${cfg.previewFontFamily} · ${cfg.previewTextSizePx}/${cfg.previewReferenceSizePx}${Strings.bandUnitPx}",
        )
    }
    AccentAction(
        if (viewModel.isSaved) Strings.bandSaved else Strings.bandSave,
        onClick = { viewModel.save() },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(Strings.bandSaveHint, fontSize = 10.5.sp, lineHeight = 15.sp, color = Tokens.HintText)
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 10.5.sp, color = Tokens.HintText, maxLines = 1, modifier = Modifier.width(74.dp))
        Text(value, fontSize = 11.5.sp, color = Tokens.OutlineText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
