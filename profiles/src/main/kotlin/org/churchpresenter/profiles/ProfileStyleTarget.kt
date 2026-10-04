package org.churchpresenter.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_only_target
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

/** A corner of half the height: the chip is a pill. */
private const val TARGET_PILL_PERCENT = 50

/**
 * The one translation the Text rows are pointed at, when it is not All: which of its values are its
 * own rather than All's, and how to give one back.
 *
 * [entryPath] is where the translation's values live -- `bibleSettings.translations[kjv.spb]` -- so
 * a row can tell which of its [SettingsRow] paths are this translation's, and [ownKeys] names its
 * fields that do not follow All.
 */
internal class StyleTarget(
    val label: String,
    val entryPath: String,
    val ownKeys: Set<String>,
    val onClear: (Collection<String>) -> Unit,
) {
    /** The fields among [paths] this translation keeps as its own. */
    fun ownFields(paths: Collection<String>): List<String> = paths
        .filter { it.startsWith("$entryPath.") }
        .map { it.removePrefix("$entryPath.").substringBefore('.') }
        .filter { it in ownKeys }
        .distinct()
}

/** The translation the Text group's rows edit, when one is picked; null under All. */
internal val LocalStyleTarget = staticCompositionLocalOf<StyleTarget?> { null }

/** "Only KJV ×" beside a value this translation has of its own: clicking gives it back to All. */
@Composable
internal fun TargetChip(paths: List<String>) {
    val target = LocalStyleTarget.current ?: return
    val own = target.ownFields(paths)
    if (own.isEmpty()) return
    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(AppShape(TARGET_PILL_PERCENT))
            .background(MaterialTheme.semantic.targetContainer)
            .clickable { target.onClear(own) }
            .padding(start = 8.dp, end = 6.dp)
            .testTag(TARGET_CHIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            stringResource(Res.string.profile_only_target, target.label),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.semantic.onTargetContainer,
            maxLines = 1,
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            tint = MaterialTheme.semantic.onTargetContainer,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** Test handle for the "Only KJV ×" chip. */
internal const val TARGET_CHIP_TAG = "profile_only_target"
