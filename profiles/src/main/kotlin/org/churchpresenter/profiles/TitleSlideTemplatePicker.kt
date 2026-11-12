package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.lower_third_animation_none
import org.churchpresenter.strings.generated.resources.title_slide_animation_kind_band
import org.churchpresenter.strings.generated.resources.title_slide_animation_kind_lower_third
import org.churchpresenter.theme.components.DropdownSelector
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** The two folders a title slide's template is offered from; either may be missing. */
internal data class TitleSlideFolders(val band: File?, val lowerThird: File?)

/** Which folder a template was found in -- what kind of file it is, and so which layers it fills. */
internal enum class TitleSlideTemplateKind { BAND, LOWER_THIRD }

/** One template the picker offers: its path, the name shown, and the folder it came from. */
internal data class TitleSlideTemplate(val path: String, val name: String, val kind: TitleSlideTemplateKind)

/**
 * Every `.json` in both [folders], by name -- the band templates and the Lower Third tab's presets
 * together, each marked with where it came from. A folder that is missing or unreadable adds none.
 */
internal fun titleSlideTemplates(folders: TitleSlideFolders): List<TitleSlideTemplate> {
    fun list(dir: File?, kind: TitleSlideTemplateKind) =
        dir?.listFiles { f -> f.isFile && f.extension.equals(TEMPLATE_EXTENSION, ignoreCase = true) }
            .orEmpty()
            .map { TitleSlideTemplate(it.absolutePath, it.nameWithoutExtension, kind) }
    // The same folder set in both places lists its files once, as band templates.
    val band = list(folders.band, TitleSlideTemplateKind.BAND)
    val lowerThird = list(folders.lowerThird, TitleSlideTemplateKind.LOWER_THIRD)
        .filter { preset -> band.none { it.path == preset.path } }
    return (band + lowerThird).sortedBy { it.name.lowercase() }
}

/**
 * The title slide's template dropdown: none first, then [titleSlideTemplates] with a tag beside
 * each saying which folder it is from. A file set from elsewhere is still listed, untagged, so
 * nothing already chosen is lost. The folders are listed off the event thread.
 */
@Composable
internal fun TitleSlideTemplatePicker(
    path: String,
    onPathChange: (String) -> Unit,
    folders: TitleSlideFolders,
    modifier: Modifier = Modifier,
) {
    val none = stringResource(Res.string.lower_third_animation_none)
    val bandTag = stringResource(Res.string.title_slide_animation_kind_band)
    val lowerThirdTag = stringResource(Res.string.title_slide_animation_kind_lower_third)
    val templates by produceState(emptyList<TitleSlideTemplate>(), folders, path) {
        value = withContext(Dispatchers.IO) { titleSlideTemplates(folders) }
    }
    val listed = templates.any { it.path == path }
    val extra = if (path.isNotBlank() && !listed) listOf(path to File(path).nameWithoutExtension) else emptyList()
    val options = listOf("" to none) + extra + templates.map { it.path to it.name }
    val kinds = templates.associate { it.path to it.kind }
    DropdownSelector(
        label = "",
        value = path,
        options = options,
        onValueChange = onPathChange,
        modifier = modifier,
        compact = true,
        itemTrailingContent = { key, _ ->
            val tag = when (kinds[key]) {
                TitleSlideTemplateKind.BAND -> bandTag
                TitleSlideTemplateKind.LOWER_THIRD -> lowerThirdTag
                null -> null
            }
            if (tag != null) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private const val TEMPLATE_EXTENSION = "json"
