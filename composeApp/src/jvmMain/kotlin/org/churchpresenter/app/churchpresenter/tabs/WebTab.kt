package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.*
import org.churchpresenter.app.churchpresenter.presenter.EmbeddedWebView
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource

@Composable
fun WebTab(
    modifier: Modifier = Modifier,
    presenterManager: PresenterManager? = null,
    selectedWebsiteItem: org.churchpresenter.app.churchpresenter.models.ScheduleItem.WebsiteItem? = null,
    onAddToSchedule: ((url: String, title: String) -> Unit)? = null,
    onUpdateScheduleTitle: ((url: String, title: String) -> Unit)? = null
) {
    // Current URL typed by the user
    var urlInput by remember { mutableStateOf("https://") }
    // URL actually loaded in the preview / sent live
    var liveUrl by remember { mutableStateOf("") }
    var isLive by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }

    // Clear snapshot when no longer live so preview shows blank
    LaunchedEffect(isLive) {
        if (!isLive) presenterManager?.setWebSnapshot(null)
    }

    // When a schedule item selects this tab, restore its URL and go live
    LaunchedEffect(selectedWebsiteItem) {
        selectedWebsiteItem?.let { item ->
            urlInput = item.url
            liveUrl = item.url
            pageTitle = item.title
            isLive = true
            presenterManager?.setWebsiteUrl(item.url)
            presenterManager?.setWebPageTitle(item.title)
            presenterManager?.setPresentingMode(Presenting.WEBSITE)
        }
    }

    // Keep the presenter in sync whenever the preview navigates to a new page
    fun onPreviewNavigated(newUrl: String) {
        if (isLive) {
            presenterManager?.setWebsiteUrl(newUrl)
        }
    }

    fun onTitleChanged(title: String) {
        pageTitle = title
        presenterManager?.setWebPageTitle(title)
        // Update the schedule item title if it was added before the page finished loading
        if (liveUrl.isNotBlank()) onUpdateScheduleTitle?.invoke(liveUrl, title)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── URL input + action buttons ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            val url = normaliseUrl(urlInput)
                            urlInput = url
                            liveUrl = url
                            true
                        } else false
                    },
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(Res.string.web_url_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                },
                label = { Text(stringResource(Res.string.web_url_label)) },
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // Go Live
            Button(
                onClick = {
                    val url = normaliseUrl(urlInput)
                    urlInput = url
                    liveUrl = url
                    isLive = true
                    presenterManager?.setWebsiteUrl(url)
                    presenterManager?.setPresentingMode(Presenting.WEBSITE)
                },
                enabled = urlInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(Res.string.web_go_live), style = MaterialTheme.typography.labelMedium)
            }

            // Add to Schedule
            if (onAddToSchedule != null) {
                Button(
                    onClick = {
                        val url = normaliseUrl(urlInput)
                        val title = pageTitle.ifBlank { url }
                        onAddToSchedule(url, title)
                    },
                    enabled = urlInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(stringResource(Res.string.web_add_to_schedule), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // ── Live badge ────────────────────────────────────────────────────────
        if (isLive) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
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
                    maxLines = 1
                )
            }
        }

        // ── Preview WebView ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isLive) 2.dp else 1.dp,
                    color = if (isLive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            if (liveUrl.isNotBlank()) {
                EmbeddedWebView(
                    url = liveUrl,
                    modifier = Modifier.fillMaxSize(),
                    onUrlChanged = { newUrl -> onPreviewNavigated(newUrl) },
                    onTitleChanged = { title -> onTitleChanged(title) },
                    onSnapshot = { bitmap ->
                        if (isLive) presenterManager?.setWebSnapshot(bitmap)
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.web_preview_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Prepend https:// if the user forgot the scheme. */
private fun normaliseUrl(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.isNotBlank() -> "https://$trimmed"
        else -> trimmed
    }
}
