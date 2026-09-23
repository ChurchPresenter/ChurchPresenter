package org.churchpresenter.calendar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_export_church_address
import org.churchpresenter.calendar.generated.resources.calendar_export_church_name
import org.churchpresenter.calendar.generated.resources.calendar_export_logo
import org.churchpresenter.calendar.generated.resources.calendar_export_logo_choose
import org.churchpresenter.calendar.generated.resources.calendar_export_logo_none
import org.churchpresenter.calendar.generated.resources.calendar_export_logo_remove
import org.churchpresenter.calendar.generated.resources.calendar_export_logo_sub
import org.churchpresenter.calendar.generated.resources.calendar_export_note
import org.churchpresenter.calendar.generated.resources.calendar_export_public_heading
import org.churchpresenter.calendar.generated.resources.calendar_export_public_note
import org.churchpresenter.calendar.generated.resources.calendar_export_sections_empty
import org.churchpresenter.calendar.model.PdfExportSettings
import org.churchpresenter.calendar.model.SectionStyle
import org.jetbrains.compose.resources.stringResource
import java.io.File

private val LOGO_PREVIEW = 44.dp
private val TEXT_FIELD = 300.dp
private val FIELD_HEIGHT = 29.dp

@Composable
internal fun ExportTab(
    settings: PdfExportSettings,
    sections: List<SectionStyle>,
    onChange: (PdfExportSettings) -> Unit,
    onChooseLogo: () -> Unit,
) {
    Text(
        text = stringResource(Res.string.calendar_export_note),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LogoRow(settings.logoPath, onChooseLogo, onRemove = { onChange(settings.copy(logoPath = "")) })
    TextRow(
        title = stringResource(Res.string.calendar_export_church_name),
        value = settings.churchName,
        onCommit = { onChange(settings.copy(churchName = it.trim())) },
    )
    TextRow(
        title = stringResource(Res.string.calendar_export_church_address),
        value = settings.churchAddress,
        onCommit = { onChange(settings.copy(churchAddress = it.trim())) },
    )

    SheetOverline(stringResource(Res.string.calendar_export_public_heading), Modifier)
    Text(
        text = stringResource(Res.string.calendar_export_public_note),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (sections.isEmpty()) {
        SettingCard { CardText(title = stringResource(Res.string.calendar_export_sections_empty), subtitle = null) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sections.forEach { section ->
            SettingCard(verticalPadding = 5.dp) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(parseHex(section.colorHex))
                )
                CardText(title = section.name, subtitle = null)
                Switch(
                    checked = settings.isStaffOnly(section.name),
                    onCheckedChange = { onChange(settings.withStaffOnly(section.name, it)) },
                )
            }
        }
    }
}

@Composable
private fun LogoRow(path: String, onChoose: () -> Unit, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val preview = remember(path) { path.takeIf { it.isNotBlank() }?.let { thumbnailOf(File(it)) } }
    SettingCard {
        Box(
            Modifier
                .size(LOGO_PREVIEW)
                .clip(RoundedCornerShape(7.dp))
                .background(scheme.surface)
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(LOGO_PREVIEW - 6.dp),
                )
            } else {
                Icon(Icons.Filled.Image, contentDescription = null, tint = scheme.onSurfaceVariant)
            }
        }
        CardText(
            title = stringResource(Res.string.calendar_export_logo),
            subtitle = if (path.isBlank()) {
                stringResource(Res.string.calendar_export_logo_none)
            } else {
                File(path).name
            } + " · " + stringResource(Res.string.calendar_export_logo_sub),
        )
        QuietButton(
            label = stringResource(Res.string.calendar_export_logo_choose),
            onClick = onChoose,
            height = SheetMetrics.rowButton,
            accent = true,
        )
        if (path.isNotBlank()) {
            SmallIconButton(
                icon = Icons.Filled.Close,
                description = stringResource(Res.string.calendar_export_logo_remove),
                onClick = onRemove,
                destructive = true,
                size = SheetMetrics.rowButton,
            )
        }
    }
}

@Composable
private fun TextRow(title: String, value: String, onCommit: (String) -> Unit) {
    var draft by remember(value) { mutableStateOf(value) }
    SettingCard {
        CardText(title = title, subtitle = null)
        key(value) {
            CompactTextField(
                value = draft,
                onValueChange = { draft = it },
                height = FIELD_HEIGHT,
                modifier = Modifier
                    .width(TEXT_FIELD)
                    .commitOnExit(draft != value) { onCommit(draft) },
            )
        }
    }
}
