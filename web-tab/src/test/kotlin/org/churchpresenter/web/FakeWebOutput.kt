package org.churchpresenter.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.cef.browser.CefBrowser

/**
 * A stand-in for the screens.
 *
 * The tab used to be driven through a real `PresenterManager`, so every test of a bookmark or a
 * zoom level also stood up the app's whole output model. The port is what the tab actually needs,
 * and this is the whole of it.
 *
 * Every property is Compose state, because in the app they are: `PresenterWebOutput` reads
 * `PresenterManager`'s observable fields, so the tab recomposes when the address, the title, the
 * snapshot or the live mode changes. Plain `var`s would leave the tab showing stale content and the
 * tests asserting something the app never does.
 *
 * [liveBrowser] is settable and defaults to null. A real `CefBrowser` cannot be constructed without
 * starting Chromium, so the tests that exercise the bridge — typing into the live page, navigating
 * it from the toolbar — hand in a `mockk`. That is the case the root `AGENT.md` names as justified:
 * there is no other way to obtain one.
 *
 * Mirrors `FakeQaOutput`, `FakeAnnouncementsOutput` and `FakeSttOutput`.
 */
internal class FakeWebOutput(
    url: String = "",
    title: String = "",
    live: Boolean = false,
) : WebOutput {

    // Backing fields are `_`-prefixed because a `var url` would compile to a `setUrl(String)` that
    // clashes with the interface's own `setUrl` at the JVM level — same signature, different origin.
    private var _url: String by mutableStateOf(url)
    private var _title: String by mutableStateOf(title)
    private var _snapshot: ImageBitmap? by mutableStateOf(null)

    override val url: String get() = _url

    override val title: String get() = _title

    override val snapshot: ImageBitmap? get() = _snapshot

    /** What the outputs are showing. Settable so a test can compose the tab already live. */
    var live: Boolean by mutableStateOf(live)

    override val isLive: Boolean get() = live

    /** The browser the outputs are drawing. Set it to put the tab in its bridged-to-live state. */
    override var liveBrowser: CefBrowser? by mutableStateOf(null)

    /** How many times Go Live was pressed. */
    var goLiveCalls: Int = 0
        private set

    override fun setUrl(url: String) {
        _url = url
    }

    override fun setTitle(title: String) {
        _title = title
    }

    override fun setSnapshot(bitmap: ImageBitmap?) {
        _snapshot = bitmap
    }

    override fun goLive() {
        goLiveCalls++
        live = true
    }
}
