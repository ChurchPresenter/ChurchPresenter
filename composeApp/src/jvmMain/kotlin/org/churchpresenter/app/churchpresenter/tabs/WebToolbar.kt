package org.churchpresenter.app.churchpresenter.tabs

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
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_web
import churchpresenter.composeapp.generated.resources.interactive_mode
import churchpresenter.composeapp.generated.resources.mirror_mode
import churchpresenter.composeapp.generated.resources.web_bookmark_add
import churchpresenter.composeapp.generated.resources.web_bookmark_remove
import churchpresenter.composeapp.generated.resources.tooltip_add_to_schedule
import churchpresenter.composeapp.generated.resources.web_clear_typed_text
import churchpresenter.composeapp.generated.resources.web_clear_url
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import churchpresenter.composeapp.generated.resources.ic_cast
import churchpresenter.composeapp.generated.resources.web_go_live
import churchpresenter.composeapp.generated.resources.web_focus_first_input
import churchpresenter.composeapp.generated.resources.web_live_badge
import churchpresenter.composeapp.generated.resources.web_type_to_page_placeholder
import churchpresenter.composeapp.generated.resources.web_url_hint
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.profileFor
import org.churchpresenter.settings.WebBookmark
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.rememberScreenDevices
import org.churchpresenter.app.churchpresenter.composables.TooltipIconButton
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken

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

    val hasSecondaryDisplay = rememberScreenDevices().size > 1
    // Web can only go live if at least one regular (non-DeckLink) fill output has showWebsite enabled
    val hasWebCapableOutput = remember(appSettings.projectionSettings) {
        val proj = appSettings.projectionSettings
        val assignments = (0 until proj.screenAssignments.size).map { proj.getAssignment(it) }
        assignments.any {
            it.targetType != Constants.TARGET_TYPE_DECKLINK && it.targetDisplay >= 0 &&
                (proj.profileFor(it)?.showWebsite ?: false)
        }
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
                NavButtons(navController, presenterManager, isLive, useInteractivePreview,
                    zoomLevel, isMobileView, ::applyZoom, ::onMobileToggle)
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
                    NavButtons(navController, presenterManager, isLive, useInteractivePreview,
                        zoomLevel, isMobileView, ::applyZoom, ::onMobileToggle)
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
            painter = painterResource(Res.drawable.ic_web),
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
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            val url = normaliseUrl(urlInput)
                            urlInput = url
                            liveUrl = url
                            presenterManager?.setWebsiteUrl(url)
                            if (isLive) {
                                presenterManager?.liveBrowser?.value?.loadURL(url)
                            }
                            true
                        } else false
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
                    painter = painterResource(Res.drawable.ic_close),
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
        onClick = {
            val url = normaliseUrl(urlInput)
            if (isBookmarked) {
                onSettingsChange { s ->
                    s.copy(webBookmarks = s.webBookmarks.filter { it.url != url })
                }
            } else {
                val title = pageTitle.ifBlank { url }
                onSettingsChange { s ->
                    s.copy(webBookmarks = s.webBookmarks + WebBookmark(url = url, title = title))
                }
            }
        },
        enabled = urlInput.isNotBlank() && urlInput != "https://",
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
            onClick = {
                val url = normaliseUrl(urlInput)
                val title = pageTitle.ifBlank { url }
                onAddToSchedule(url, title)
            },
            enabled = urlInput.isNotBlank(),
            tooltipText = stringResource(Res.string.tooltip_add_to_schedule)
        )
    }

    // Go Live
    val goLiveEnabled = urlInput.isNotBlank() && hasSecondaryDisplay && hasWebCapableOutput
    GoLiveButton(
        onClick = {
            val url = normaliseUrl(urlInput)
            urlInput = url
            liveUrl = url
            presenterManager?.setWebsiteUrl(url)
            presenterManager?.setPresentingMode(Presenting.WEBSITE)
        },
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
                modifier = Modifier.clickable {
                    urlInput = bookmark.url
                    liveUrl = bookmark.url
                    pageTitle = bookmark.title
                    presenterManager?.setWebsiteUrl(bookmark.url)
                    if (isLive) {
                        presenterManager?.liveBrowser?.value?.loadURL(bookmark.url)
                    }
                }
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
                        modifier = Modifier.clickable {
                            onSettingsChange { s ->
                                s.copy(webBookmarks = s.webBookmarks.filter { it.url != bookmark.url })
                            }
                        }
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
            modifier = Modifier.clickable { useInteractivePreview = !useInteractivePreview }
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
                    onValueChange = { next ->
                        val browser = presenterManager?.liveBrowser?.value
                        if (browser == null) { typeBuffer = next; return@BasicTextField }
                        val old = typeBuffer
                        val common = commonPrefixLength(old, next)
                        val toDelete = old.length - common
                        val toInsert = next.substring(common)
                        repeat(toDelete) { browser.executeJavaScript(WEB_JS_BACKSPACE, "", 0) }
                        toInsert.forEach { ch -> browser.executeJavaScript(jsInsert(ch), "", 0) }
                        typeBuffer = next
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                presenterManager?.liveBrowser?.value
                                    ?.executeJavaScript(WEB_JS_ENTER, "", 0)
                                typeBuffer = ""
                                true
                            } else false
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
                        painter = painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.web_clear_typed_text),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TooltipIconButton(
            painter = painterResource(Res.drawable.ic_cast),
            text = stringResource(Res.string.web_focus_first_input),
            onClick = {
                presenterManager?.liveBrowser?.value
                    ?.executeJavaScript(WEB_JS_FOCUS_FIRST_INPUT, "", 0)
            }
        )
    }
}
