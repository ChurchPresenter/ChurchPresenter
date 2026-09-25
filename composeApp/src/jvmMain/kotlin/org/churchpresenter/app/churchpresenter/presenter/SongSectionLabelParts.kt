package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.SongSectionLabel

/**
 * The section label's own two pieces, lifted out of `SongPresenter.kt`.
 *
 * Not because they do not belong there -- they are only used there -- but because that file is at
 * detekt's per-file function ceiling, which is the signal it has become large enough that anything
 * separable should be separate.
 */

/**
 * [content], with [floatingContent] positioned over it when [floating] -- and untouched when not.
 *
 * The "and untouched when not" is the point. Wrapping the content in a `Box` unconditionally would
 * be simpler to read and wrong: the box fills the content area, so the alignment the caller set on
 * the column inside it stops placing anything. Only a slide that actually has something floating
 * pays for the extra layer, and every existing slide takes the path it always took.
 */
@Composable
internal fun SongContentFrame(
    floating: Boolean,
    floatingContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!floating) {
        content()
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        floatingContent()
    }
}

/**
 * The section label to draw, or null when there is nothing to draw or it is switched off.
 *
 * The brackets come from the data, not from here: `LyricSection.header` is the header line as the
 * song file wrote it, `[Verse 1]` or `{Chorus}`, and the label showed them because it drew that
 * string as it stood. `shouldShowText` has stripped them for its own purposes all along; this is
 * the same strip, so the label reads "Verse 1" on screen the way the settings preview always
 * promised it would. A song with no header falls back to its humanized type, which has no brackets
 * to lose.
 */
internal fun sectionLabelText(
    section: LyricSection,
    settings: SongSectionLabel,
    isTitleSlide: Boolean,
): String? {
    if (!settings.enabled || isTitleSlide) return null
    val header = section.header?.takeIf { it.isNotBlank() }
    val raw = header ?: section.type.replaceFirstChar { it.uppercase() }
    val stripped = raw.trim()
        .removePrefix("[").removePrefix("{")
        .removeSuffix("]").removeSuffix("}")
        .trim()
    return stripped.takeIf { it.isNotBlank() }
}
