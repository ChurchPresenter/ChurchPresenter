package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_catalog_installed_count
import churchpresenter.composeapp.generated.resources.bible_catalog_language_all
import churchpresenter.composeapp.generated.resources.bible_catalog_language_count
import churchpresenter.composeapp.generated.resources.bible_catalog_language_named
import churchpresenter.composeapp.generated.resources.bible_catalog_language_named_native
import churchpresenter.composeapp.generated.resources.bible_catalog_retry
import churchpresenter.composeapp.generated.resources.bible_catalog_search_placeholder
import churchpresenter.composeapp.generated.resources.bible_catalog_stale_notice
import churchpresenter.composeapp.generated.resources.bible_catalog_subtitle
import churchpresenter.composeapp.generated.resources.bible_catalog_title
import org.churchpresenter.app.churchpresenter.composables.PaneTab
import org.churchpresenter.app.churchpresenter.composables.PaneTabRow
import org.churchpresenter.app.churchpresenter.composables.SearchableDropdownField
import org.churchpresenter.app.churchpresenter.viewmodel.BibleCatalogViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.BibleDownloadError
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp
) {
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
internal fun Header(installedCount: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        IconBadge(icon = Icons.Filled.Book)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.bible_catalog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.bible_catalog_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.inverseOnSurface, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.bible_catalog_installed_count, installedCount),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * The catalogue source picker — the same recessed-track tab strip the song editor uses for its
 * lyric panes, so the two read as one control rather than two dialogs' takes on the same idea.
 */
@Composable
internal fun SourceSegmentedControl(
    tabLabels: List<String>,
    selectedTab: Int,
    onSelect: (Int) -> Unit
) {
    PaneTabRow {
        tabLabels.forEachIndexed { index, label ->
            PaneTab(label = label, selected = index == selectedTab) { onSelect(index) }
        }
    }
}

@Composable
internal fun LanguageDropdown(
    languages: List<BibleCatalogViewModel.LanguageOption>,
    selectedLanguage: String?,
    onLanguageChange: (String?) -> Unit,
) {
    val allLanguagesLabel = stringResource(Res.string.bible_catalog_language_all)

    val languageLabels = languages.associate { option ->

        val names = listOf(option.name, option.nativeName)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val label = when (names.size) {
            0 -> stringResource(Res.string.bible_catalog_language_count, option.code, option.count)
            1 -> stringResource(Res.string.bible_catalog_language_named, names[0], option.code, option.count)
            else -> stringResource(
                Res.string.bible_catalog_language_named_native,
                names[0], names[1], option.code, option.count
            )
        }
        label to option.code
    }
    val selectedLabel = languageLabels.entries
        .firstOrNull { it.value == selectedLanguage }?.key
        ?: allLanguagesLabel

    SearchableDropdownField(
        value = selectedLabel,
        options = listOf(allLanguagesLabel) + languageLabels.keys,
        onValueChange = { label -> onLanguageChange(languageLabels[label]) },
        leadingIcon = {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },

        horizontalPadding = 11.dp,
        menuWidth = 340.dp,
        fillWidth = true,

        onClear = { onLanguageChange(null) }.takeIf { selectedLanguage != null },
        itemContent = { option ->
            Text(
                text = option,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        clearOnFocus = true,
        modifier = Modifier.width(220.dp)
    )
}

internal val SearchFieldShape = RoundedCornerShape(10.dp)

@Composable
internal fun SearchField(viewModel: BibleCatalogViewModel) {
    BasicTextField(
        value = viewModel.query,
        onValueChange = { viewModel.query = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), SearchFieldShape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), SearchFieldShape),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (viewModel.query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.bible_catalog_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
internal fun Messages(viewModel: BibleCatalogViewModel, onRetryInstall: () -> Unit) {
    if (viewModel.isStale) {
        Text(
            text = stringResource(Res.string.bible_catalog_stale_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))
    }
    viewModel.catalogError?.let { error ->
        Text(
            text = stringResource(catalogErrorStringRes(error)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
    }
    viewModel.installError?.let { error ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(installErrorStringRes(error)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            if (error == BibleDownloadError.DOWNLOAD_STALLED) {
                TextButton(onClick = onRetryInstall) {
                    Text(stringResource(Res.string.bible_catalog_retry))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
