package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_catalog_license_accept
import churchpresenter.composeapp.generated.resources.bible_catalog_license_badge_redistributable
import churchpresenter.composeapp.generated.resources.bible_catalog_license_badge_unverified
import churchpresenter.composeapp.generated.resources.bible_catalog_license_body
import churchpresenter.composeapp.generated.resources.bible_catalog_license_field_copyright
import churchpresenter.composeapp.generated.resources.bible_catalog_license_field_identifier
import churchpresenter.composeapp.generated.resources.bible_catalog_license_field_source
import churchpresenter.composeapp.generated.resources.bible_catalog_license_subtitle
import churchpresenter.composeapp.generated.resources.bible_catalog_license_title
import churchpresenter.composeapp.generated.resources.bible_catalog_license_unknown
import churchpresenter.composeapp.generated.resources.bible_catalog_overwrite_confirm
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.bible_catalog_book_names_english
import org.churchpresenter.app.churchpresenter.data.BebliaSource
import org.churchpresenter.app.churchpresenter.data.BibleModule
import org.churchpresenter.app.churchpresenter.data.BibleSourceId
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LicenceConfirmation(
    module: BibleModule,
    isReinstall: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isRedistributable = module.sourceId == BibleSourceId.EBIBLE
    val showsEnglishBookNames = module.sourceId == BibleSourceId.BEBLIA &&
        !BebliaSource.hasLocalisedBookNames(module.language)
    val badgeContainer = if (isRedistributable) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.errorContainer
    val badgeContent = if (isRedistributable) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onErrorContainer
    val badgeLabel = stringResource(
        if (isRedistributable) Res.string.bible_catalog_license_badge_redistributable
        else Res.string.bible_catalog_license_badge_unverified
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconBadge(icon = Icons.Filled.Copyright) },
        title = {
            Column {
                Text(stringResource(Res.string.bible_catalog_license_title),
                    style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(Res.string.bible_catalog_license_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = module.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = MaterialTheme.shapes.extraSmall,
                            color = badgeContainer,
                            contentColor = badgeContent) {
                            Text(
                                text = badgeLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    MetadataRow(
                        label = stringResource(Res.string.bible_catalog_license_field_source),
                        value = stringResource(sourceLabelStringRes(module.sourceId))
                    )
                    MetadataRow(
                        label = stringResource(Res.string.bible_catalog_license_field_identifier),
                        value = module.identifier
                    )
                    MetadataRow(
                        label = stringResource(Res.string.bible_catalog_license_field_copyright),
                        value = if (module.copyright.isNotBlank()) {
                            module.copyright
                        } else {
                            stringResource(Res.string.bible_catalog_license_unknown)
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {

                        Text(
                            text = stringResource(sourceLicenceStringRes(module.sourceId)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.bible_catalog_license_body),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (showsEnglishBookNames) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.bible_catalog_book_names_english),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isReinstall) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.bible_catalog_overwrite_confirm, module.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(6.dp)) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.bible_catalog_license_accept))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(6.dp)) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
internal fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
