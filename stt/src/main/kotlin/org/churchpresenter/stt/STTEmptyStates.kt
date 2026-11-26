package org.churchpresenter.stt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.churchpresenter.icons.generated.resources.ic_mic
import org.churchpresenter.icons.generated.resources.ic_play
import org.churchpresenter.icons.generated.resources.ic_refresh
import org.churchpresenter.icons.generated.resources.ic_warning_outline
import org.churchpresenter.sharedui.composables.EmptyStateButton
import org.churchpresenter.sharedui.composables.EmptyStateContent
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.stt_empty_body
import org.churchpresenter.strings.generated.resources.stt_empty_connect
import org.churchpresenter.strings.generated.resources.stt_empty_connecting
import org.churchpresenter.strings.generated.resources.stt_empty_connecting_body
import org.churchpresenter.strings.generated.resources.stt_empty_title
import org.churchpresenter.strings.generated.resources.stt_error_body
import org.churchpresenter.strings.generated.resources.stt_error_retry
import org.churchpresenter.strings.generated.resources.stt_error_title
import org.churchpresenter.strings.generated.resources.stt_listening_body
import org.churchpresenter.strings.generated.resources.stt_listening_title
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/**
 * No server connected: what the tab shows once one is, and connecting to the address typed above --
 * or, when the last try failed, which server could not be reached and trying it again.
 */
@Composable
internal fun STTNotConnected(sttManager: STTManager, urlInput: String, onConnect: (String) -> Unit) {
    val connecting by sttManager.connecting
    val reconnecting by sttManager.reconnecting
    val connectError by sttManager.connectError
    val working = connecting || reconnecting
    val url = connectUrl(urlInput)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (connectError && !working) {
            EmptyStateContent(
                icon = IconRes.drawable.ic_warning_outline,
                neutral = false,
                error = true,
                title = stringResource(Res.string.stt_error_title, serverName(url)),
                body = stringResource(Res.string.stt_error_body),
            ) {
                EmptyStateButton(stringResource(Res.string.stt_error_retry), IconRes.drawable.ic_refresh) {
                    onConnect(urlInput)
                }
            }
        } else {
            EmptyStateContent(
                icon = IconRes.drawable.ic_mic,
                neutral = false,
                title = stringResource(Res.string.stt_empty_title),
                body = if (working) {
                    stringResource(Res.string.stt_empty_connecting_body, url)
                } else {
                    stringResource(Res.string.stt_empty_body)
                },
            ) {
                // Held while a connect is under way, as the bar's own Connect is.
                if (working) {
                    EmptyStateButton(stringResource(Res.string.stt_empty_connecting), IconRes.drawable.ic_play) {}
                } else if (isConnectable(urlInput)) {
                    EmptyStateButton(stringResource(Res.string.stt_empty_connect), IconRes.drawable.ic_play) {
                        onConnect(urlInput)
                    }
                }
            }
        }
    }
}

/** Connected, and nothing said yet. */
@Composable
internal fun STTListening() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyStateContent(
            icon = IconRes.drawable.ic_mic,
            neutral = true,
            compact = true,
            title = stringResource(Res.string.stt_listening_title),
            body = stringResource(Res.string.stt_listening_body),
        )
    }
}

/** A server address as a person names it: without its scheme or a trailing slash. */
internal fun serverName(url: String): String =
    url.removePrefix("http://").removePrefix("https://").trimEnd('/').ifEmpty { url }

/** Whether [typed] names a server at all, rather than a bare scheme or nothing. */
internal fun isConnectable(typed: String): Boolean =
    typed.isNotBlank() && serverName(connectUrl(typed.trim())).isNotBlank() &&
        typed.trim() != "http://" && typed.trim() != "https://"
