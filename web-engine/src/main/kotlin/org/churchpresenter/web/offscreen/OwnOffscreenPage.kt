package org.churchpresenter.web.offscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.cef.browser.CefBrowser
import org.churchpresenter.web.presenter.WebNavController

/**
 * A page of this composable's own, rendered off screen and controlled here: what the Web tab's
 * preview browses with before a page goes live. It opens with the composable and closes with it,
 * renders at this box's size, and is steered through [navController] like a windowed browser.
 * Its sessions are not reported to Sentry; only the live page's are. [open] opens the page, a seam
 * for tests.
 */
@Composable
fun OwnOffscreenPage(
    url: String,
    modifier: Modifier,
    onUrlChanged: ((String) -> Unit)?,
    onTitleChanged: ((String) -> Unit)?,
    navController: WebNavController?,
    onBrowserCreated: ((CefBrowser) -> Unit)?,
    open: (PageRequest) -> OffscreenPage? = { request ->
        OffscreenPage.open(
            url = request.url,
            onUrlChanged = request.onUrlChanged,
            onTitleChanged = request.onTitleChanged,
            reports = false,
            mobileMode = request.mobileMode,
        )
    },
) {
    val page = remember { mutableStateOf<OffscreenPage?>(null) }
    val onUrl = rememberUpdatedState(onUrlChanged)
    val onTitle = rememberUpdatedState(onTitleChanged)
    DisposableEffect(Unit) {
        val opened = open(
            PageRequest(
                url = url,
                onUrlChanged = { onUrl.value?.invoke(it) },
                onTitleChanged = { onTitle.value?.invoke(it) },
                mobileMode = { navController?.mobileMode == true },
            ),
        )
        page.value = opened
        if (opened != null) {
            navController?.browser = opened.browser
            onBrowserCreated?.invoke(opened.browser)
        }
        onDispose {
            if (opened != null && navController?.browser === opened.browser) navController.browser = null
            opened?.close()
        }
    }
    val current = page.value
    if (current == null) {
        Box(modifier.background(Color.Black))
        return
    }
    LaunchedEffect(current, url) { current.load(url) }
    OffscreenWebControl(current, modifier.background(Color.Black), drivesSize = true)
}

/** What [OwnOffscreenPage] asks a page to be opened with. */
class PageRequest(
    val url: String,
    val onUrlChanged: (String) -> Unit,
    val onTitleChanged: (String) -> Unit,
    val mobileMode: () -> Boolean,
)
