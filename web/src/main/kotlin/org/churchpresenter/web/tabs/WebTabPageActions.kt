package org.churchpresenter.web.tabs

import org.churchpresenter.settings.WebBookmark
import org.churchpresenter.sharedui.models.Presenting

/** Whether the address bar holds anything worth saving: not empty, and not just the scheme it starts on. */
internal val WebTabScope.hasAddress: Boolean get() = urlInput.isNotBlank() && urlInput != "https://"

/** Bookmarks the address, under the page's title when it has one — or removes it, if it already is one. */
internal fun WebTabScope.toggleBookmark() {
    val url = currentUrlNormalised
    if (isBookmarked) {
        onSettingsChange { s -> s.copy(webBookmarks = s.webBookmarks.filter { it.url != url }) }
    } else {
        val title = pageTitle.ifBlank { url }
        onSettingsChange { s -> s.copy(webBookmarks = s.webBookmarks + WebBookmark(url = url, title = title)) }
    }
}

/** Adds the address to the schedule, under the page's title when it has one. */
internal fun WebTabScope.addToSchedule() {
    val url = currentUrlNormalised
    onAddToSchedule?.invoke(url, pageTitle.ifBlank { url })
}

/** Puts the address on screen. */
internal fun WebTabScope.goLive() {
    val url = currentUrlNormalised
    urlInput = url
    liveUrl = url
    output?.setWebsiteUrl(url)
    output?.setPresentingMode(Presenting.WEBSITE)
}

/** Opens [bookmark] in the preview — and in the live window while live. */
internal fun WebTabScope.openBookmark(bookmark: WebBookmark) {
    urlInput = bookmark.url
    liveUrl = bookmark.url
    pageTitle = bookmark.title
    output?.setWebsiteUrl(bookmark.url)
    if (isLive) output?.liveBrowser?.value?.loadURL(bookmark.url)
}

/** Forgets [bookmark]. */
internal fun WebTabScope.removeBookmark(bookmark: WebBookmark) {
    onSettingsChange { s -> s.copy(webBookmarks = s.webBookmarks.filter { it.url != bookmark.url }) }
}

/**
 * Takes [next] as the type-to-page text, and types the difference into the live page: a backspace
 * for each character taken off the end of what was there, then each new character.
 */
internal fun WebTabScope.typeToPage(next: String) {
    val browser = output?.liveBrowser?.value
    if (browser != null) {
        val common = commonPrefixLength(typeBuffer, next)
        repeat(typeBuffer.length - common) { browser.executeJavaScript(WEB_JS_BACKSPACE, "", 0) }
        next.substring(common).forEach { ch -> browser.executeJavaScript(jsInsert(ch), "", 0) }
    }
    typeBuffer = next
}

/** Presses Enter in the live page, and starts the type-to-page text afresh. */
internal fun WebTabScope.submitTypeToPage() {
    output?.liveBrowser?.value?.executeJavaScript(WEB_JS_ENTER, "", 0)
    typeBuffer = ""
}

/** Puts the live page's focus in its first text field, so what is typed here has somewhere to go. */
internal fun WebTabScope.focusFirstInput() {
    output?.liveBrowser?.value?.executeJavaScript(WEB_JS_FOCUS_FIRST_INPUT, "", 0)
}
