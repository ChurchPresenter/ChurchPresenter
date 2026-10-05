package org.churchpresenter.web.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import org.churchpresenter.theme.components.KeyIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_web
import org.churchpresenter.strings.generated.resources.interactive_mode
import org.churchpresenter.strings.generated.resources.mirror_mode
import org.churchpresenter.strings.generated.resources.web_bookmark_add
import org.churchpresenter.strings.generated.resources.web_bookmark_remove
import org.churchpresenter.strings.generated.resources.tooltip_add_to_schedule
import org.churchpresenter.strings.generated.resources.web_clear_typed_text
import org.churchpresenter.strings.generated.resources.web_clear_url
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import org.churchpresenter.icons.generated.resources.ic_cast
import org.churchpresenter.strings.generated.resources.web_go_live
import org.churchpresenter.strings.generated.resources.web_focus_first_input
import org.churchpresenter.strings.generated.resources.web_live_badge
import org.churchpresenter.strings.generated.resources.web_type_to_page_placeholder
import org.churchpresenter.strings.generated.resources.web_url_hint
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.profileFor
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.sharedui.composables.ActionIconButton
import org.churchpresenter.sharedui.composables.AddToScheduleButton
import org.churchpresenter.sharedui.composables.GoLiveButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken
import org.churchpresenter.sharedui.composables.searchBarCard

/** The top card: the toolbar, the bookmarks, and while live, the live bar and typing to the page. */
@Composable
internal fun WebTabScope.WebToolbarCard() {
    Column(
        modifier = Modifier.fillMaxWidth().searchBarCard(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WebToolbar()

        // ── Horizontal bookmarks bar ───────────────────────────────────────
        if (bookmarks.isNotEmpty()) {
            WebBookmarksBar()
        }

        // ── Live badge + preview mode toggle ──────────────────────────────
        if (isLive) {
            WebLiveBadgeRow()

                // ── "Type to page" input for the live mirror ──
                // Only useful in mirror mode — interactive mode already accepts native typing.
                if (!useInteractivePreview) {
                WebTypeToPage()
            }
        }
    }
}

/** Navigation, the address and the actions: one row when there is room, two when there is not. */
@Composable
private fun WebTabScope.WebToolbar() {
    // ── Toolbar: nav + URL + actions (1 or 2 rows based on width) ──
    // Approximate width consumed by nav buttons + zoom + desktop toggle + action buttons
    val navButtonsWidth = 440.dp   // 4 icon buttons + zoom controls + desktop toggle
    val actionButtonsWidth = 320.dp // bookmark + Add to Schedule + Go Live
    val minUrlWidth = 200.dp

    val hasWebCapableOutput = remember(appSettings.projectionSettings) {
        hasWebCapableOutput(appSettings.projectionSettings)
    }

    // Shared composables for URL bar and action buttons
    val urlBar: @Composable RowScope.() -> Unit = { WebUrlBar(Modifier.weight(1f).widthIn(min = minUrlWidth)) }
    val actionButtons: @Composable RowScope.() -> Unit = { WebActionButtons(hasSecondaryDisplay, hasWebCapableOutput) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val singleRow = maxWidth >= navButtonsWidth + minUrlWidth + actionButtonsWidth

        if (singleRow) {
            // Everything on one line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavButtons()
                urlBar()
                actionButtons()
            }
        } else {
            // Two rows: nav + actions on top, URL bar below
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavButtons()
                    Spacer(Modifier.weight(1f))
                    actionButtons()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    urlBar()
                }
            }
        }
    }
}

@Composable
private fun WebTabScope.WebUrlBar(modifier: Modifier) {
    Row(
        modifier = modifier
            .height(42.dp)
            .sunken(AppShape(8.dp), elevationPalette())
            .hoverTint(AppShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(IconRes.drawable.ic_web),
            contentDescription = null,
            modifier = Modifier.padding(start = 11.dp).size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            BasicTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onKeyEvent { event ->
                        val submit = event.type == KeyEventType.KeyUp && event.key == Key.Enter
                        if (submit) submitUrl()
                        submit
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (urlInput.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.web_url_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            )
        }
        if (urlInput.isNotEmpty() && urlInput != "https://") {
            KeyIconButton(
                onClick = { urlInput = "" },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    painter = painterResource(IconRes.drawable.ic_close),
                    contentDescription = stringResource(Res.string.web_clear_url),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Bookmark, Add to Schedule and Go Live. */
@Composable
private fun WebTabScope.WebActionButtons(hasSecondaryDisplay: Boolean, hasWebCapableOutput: Boolean) {
    // Star bookmark toggle
    ActionIconButton(
        onClick = { toggleBookmark() },
        enabled = hasAddress,
        tooltipText = stringResource(if (isBookmarked) Res.string.web_bookmark_remove else Res.string.web_bookmark_add),
        icon = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
        containerColor = if (isBookmarked) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (isBookmarked) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )

    // Add to Schedule
    if (onAddToSchedule != null) {
        AddToScheduleButton(
            onClick = { addToSchedule() },
            enabled = urlInput.isNotBlank(),
            tooltipText = stringResource(Res.string.tooltip_add_to_schedule)
        )
    }

    // Go Live
    val goLiveEnabled = urlInput.isNotBlank() && hasSecondaryDisplay && hasWebCapableOutput
    GoLiveButton(
        onClick = { goLive() },
        enabled = goLiveEnabled,
        tooltipText = stringResource(Res.string.web_go_live)
    )
}

@Composable
private fun WebTabScope.WebBookmarksBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                AppShape(4.dp),
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bookmarks.forEach { bookmark ->
            Surface(
                shape = AppShape(4.dp),
                color = if (liveUrl == bookmark.url) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                modifier = Modifier.clickable { openBookmark(bookmark) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = bookmark.title.ifBlank { bookmark.url },
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                    Text(
                        text = "\u2715",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { removeBookmark(bookmark) }
                    )
                }
            }
        }
    }
}

/** The live badge, the page on screen, and the mirror/interactive toggle. */
@Composable
private fun WebTabScope.WebLiveBadgeRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = AppShape(4.dp),
            color = MaterialTheme.colorScheme.error
        ) {
            Text(
                text = stringResource(Res.string.web_live_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(
            text = if (pageTitle.isNotBlank()) pageTitle else liveUrl,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        // Toggle between screenshot mirror and interactive preview
        Surface(
            shape = AppShape(4.dp),
            color = if (useInteractivePreview) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { toggleInteractivePreview() }
        ) {
            Text(
                text = stringResource(
                    if (useInteractivePreview) Res.string.interactive_mode else Res.string.mirror_mode,
                ),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun WebTabScope.WebTypeToPage() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .sunken(AppShape(8.dp), elevationPalette())
                .hoverTint(AppShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                BasicTextField(
                    value = typeBuffer,
                    onValueChange = { typeToPage(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onKeyEvent { event ->
                            val submit = event.type == KeyEventType.KeyDown && event.key == Key.Enter
                            if (submit) submitTypeToPage()
                            submit
                        },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (typeBuffer.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.web_type_to_page_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                )
            }
            if (typeBuffer.isNotEmpty()) {
                KeyIconButton(onClick = { typeBuffer = "" }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        painter = painterResource(IconRes.drawable.ic_close),
                        contentDescription = stringResource(Res.string.web_clear_typed_text),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TooltipIconButton(
            painter = painterResource(IconRes.drawable.ic_cast),
            text = stringResource(Res.string.web_focus_first_input),
            onClick = { focusFirstInput() }
        )
    }
}

/**
 * Whether a website can go live anywhere: on at least one regular (not DeckLink) output, assigned to
 * a display, whose profile shows websites.
 */
internal fun hasWebCapableOutput(proj: ProjectionSettings): Boolean =
    proj.screenAssignments.any {
        it.targetType != Constants.TARGET_TYPE_DECKLINK && it.targetDisplay >= 0 &&
            proj.profileFor(it)?.look?.slide?.web == true
    }
