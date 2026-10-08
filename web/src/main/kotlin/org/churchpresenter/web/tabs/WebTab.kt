package org.churchpresenter.web.tabs

import org.churchpresenter.sharedui.composables.goLiveKeyTarget
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.core.models.schedule.ScheduleItem
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.desktop_view
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_left
import org.churchpresenter.icons.generated.resources.ic_arrow_right
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.churchpresenter.icons.generated.resources.ic_clear_cache
import org.churchpresenter.icons.generated.resources.ic_refresh
import org.churchpresenter.strings.generated.resources.mobile_view
import org.churchpresenter.strings.generated.resources.web_back
import org.churchpresenter.strings.generated.resources.web_engine_unavailable_body
import org.churchpresenter.strings.generated.resources.web_engine_unavailable_library_body
import org.churchpresenter.strings.generated.resources.web_engine_unavailable_library_title
import org.churchpresenter.strings.generated.resources.web_engine_unavailable_policy_body
import org.churchpresenter.strings.generated.resources.web_engine_unavailable_policy_title
import org.churchpresenter.strings.generated.resources.web_engine_unavailable_title
import org.churchpresenter.strings.generated.resources.web_engine_unavailable_macos_body
import org.churchpresenter.strings.generated.resources.web_engine_unavailable_macos_title
import org.churchpresenter.strings.generated.resources.web_engine_unavailable_windows_body
import org.churchpresenter.strings.generated.resources.web_engine_unavailable_windows_title
import org.churchpresenter.strings.generated.resources.web_clear_cache
import org.churchpresenter.strings.generated.resources.web_forward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import org.churchpresenter.strings.generated.resources.web_refresh
import org.churchpresenter.strings.generated.resources.web_zoom_in
import org.churchpresenter.strings.generated.resources.web_zoom_out
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.web.presenter.CefManager
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.web.presenter.rememberWebNavController
import org.churchpresenter.web.WebOutput
import org.churchpresenter.sharedui.utils.rememberScreenDevices
import org.churchpresenter.sharedui.composables.ActionIconButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A 16:9 output, for a preview with no output to measure. */
private const val DEFAULT_PREVIEW_ASPECT_RATIO = 16f / 9f

internal const val WEB_SNAPSHOT_RETRY_DELAY_MS = 7000L
private const val ZOOM_FACTOR = 1.2
private const val PERCENT_SCALE = 100
private const val FIRST_PRINTABLE_CHAR = 0x20

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebTab(
    modifier: Modifier = Modifier,
    output: WebOutput? = null,
    selectedWebsiteItem: ScheduleItem.WebsiteItem? = null,
    /**
     * Bumped by the caller on every schedule click, so clicking the *same* item twice re-runs the
     * effect below. Keyed on the item alone, an unchanged item is an unchanged key and the second
     * click does nothing.
     */
    selectedWebsiteItemVersion: Int = 0,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((url: String, title: String) -> Unit)? = null,
    onUpdateScheduleTitle: ((url: String, title: String) -> Unit)? = null,
    /** Overridable so tests can reach both branches without touching the real JCEF singleton. */
    cefInitialized: Boolean = CefManager.initialized,
    cefMacOsUnsupported: Boolean = CefManager.macOsUnsupported,
    cefBlockedByPolicy: Boolean = CefManager.blockedByPolicy,
    cefMissingLibrary: String? = CefManager.missingLibrary,
    /** Whether a screen beyond the operator's own is attached; overridable for the same reason. */
    hasSecondaryDisplay: Boolean = rememberScreenDevices().size > 1,
    /**
     * Width over height of the output the preview stands in for. The app reads it from the preview
     * output the operator picked, so the embedded browser lays the page out at the shape it goes
     * out at.
     */
    previewAspectRatio: Float = DEFAULT_PREVIEW_ASPECT_RATIO,
    /** The output picker above the preview, drawn by the app, which owns the outputs. */
    outputPicker: @Composable (Modifier) -> Unit = {},
) {
    // JCEF's native engine can fail to load at startup (broken chrome_elf.dll, missing
    // VC++ runtime, etc.). CefManager.init() catches that and leaves the engine down for
    // the whole session, so show an actionable panel instead of dead browser chrome.
    if (!cefInitialized) {
        WebEngineUnavailable(modifier, cefMacOsUnsupported, cefBlockedByPolicy, missingLibrary = cefMissingLibrary)
        return
    }

    // Restore URL / title from PresenterManager so state survives tab switches
    val savedUrl = output?.websiteUrl?.value ?: ""
    val savedTitle = output?.webPageTitle?.value ?: ""
    val state = remember { WebTabState(savedUrl, savedTitle) }

    // Derived from what is on air — clears automatically on "Clear Display"
    val isLive = output?.isLive(Presenting.WEBSITE) == true

    val navController = rememberWebNavController()
    // Remembered, keyed on everything it holds: a new scope on every recomposition would hand the
    // pieces new lambdas each time, and a click handler keyed on its lambda would restart.
    val tab = remember(
        output, appSettings, onSettingsChange, onAddToSchedule, onUpdateScheduleTitle, state, isLive,
        navController, previewAspectRatio, outputPicker, hasSecondaryDisplay
    ) {
        WebTabScope(
            output = output,
            appSettings = appSettings,
            onSettingsChange = onSettingsChange,
            onAddToSchedule = onAddToSchedule,
            onUpdateScheduleTitle = onUpdateScheduleTitle,
            state = state,
            isLive = isLive,
            navController = navController,
            previewAspectRatio = previewAspectRatio,
            outputPicker = outputPicker,
            hasSecondaryDisplay = hasSecondaryDisplay,
        )
    }
    tab.WebTabEffects(selectedWebsiteItem, selectedWebsiteItemVersion)

    // Go Live on a key, under the button's condition, and not for the page already on screen.
    val webCapable = remember(appSettings.projectionSettings) { hasWebCapableOutput(appSettings.projectionSettings) }
    val canGoLive = tab.urlInput.isNotBlank() && hasSecondaryDisplay && webCapable &&
        !(isLive && tab.liveUrl == tab.currentUrlNormalised)
    Column(modifier = modifier.fillMaxSize().goLiveKeyTarget(enabled = canGoLive) { tab.goLive() }) {
        tab.WebToolbarCard()
        // The output picker and the preview share one card.
        tab.WebPreviewCard(Modifier.weight(1f))
    }
}


/**
 * Shown in place of the browser when JCEF's native engine failed to load at startup.
 * Points the user at the Microsoft Visual C++ Redistributable, the most common cause.
 */
@Composable
internal fun WebEngineUnavailable(
    modifier: Modifier = Modifier,
    macOsUnsupported: Boolean = CefManager.macOsUnsupported,
    // A managed Windows build can block the downloaded engine outright, and telling someone in that
    // position to install a redistributable sends them after something that will not help.
    blockedByPolicy: Boolean = CefManager.blockedByPolicy,
    windowsUnsupported: Boolean = CefManager.windowsUnsupported,
    // On Linux the usual cause is a library the distribution did not install, which the
    // Windows-only advice below would only confuse.
    missingLibrary: String? = CefManager.missingLibrary,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                when {
                    macOsUnsupported -> Res.string.web_engine_unavailable_macos_title
                    windowsUnsupported -> Res.string.web_engine_unavailable_windows_title
                    blockedByPolicy -> Res.string.web_engine_unavailable_policy_title
                    missingLibrary != null -> Res.string.web_engine_unavailable_library_title
                    else -> Res.string.web_engine_unavailable_title
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when {
                macOsUnsupported -> stringResource(Res.string.web_engine_unavailable_macos_body)
                windowsUnsupported -> stringResource(Res.string.web_engine_unavailable_windows_body)
                blockedByPolicy -> stringResource(Res.string.web_engine_unavailable_policy_body)
                missingLibrary != null -> stringResource(Res.string.web_engine_unavailable_library_body, missingLibrary)
                else -> stringResource(Res.string.web_engine_unavailable_body)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp)
        )
    }
}

@Composable
internal fun WebTabScope.NavButtons() {
    ActionIconButton(
        onClick = { goBack() },
        tooltipText = stringResource(Res.string.web_back),
        painter = painterResource(IconRes.drawable.ic_arrow_left),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ActionIconButton(
        onClick = { goForward() },
        tooltipText = stringResource(Res.string.web_forward),
        painter = painterResource(IconRes.drawable.ic_arrow_right),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ActionIconButton(
        onClick = { refresh() },
        tooltipText = stringResource(Res.string.web_refresh),
        painter = painterResource(IconRes.drawable.ic_refresh),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    ActionIconButton(
        onClick = { clearWebCache() },
        tooltipText = stringResource(Res.string.web_clear_cache),
        painter = painterResource(IconRes.drawable.ic_clear_cache),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    )

    // Zoom out
    ActionIconButton(
        onClick = { stepZoom(zoomIn = false) },
        tooltipText = stringResource(Res.string.web_zoom_out),
        painter = painterResource(IconRes.drawable.ic_arrow_down),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Zoom percentage
    Text(
        text = "${(Math.pow(ZOOM_FACTOR, zoomLevel) * PERCENT_SCALE).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Zoom in
    ActionIconButton(
        onClick = { stepZoom(zoomIn = true) },
        tooltipText = stringResource(Res.string.web_zoom_in),
        painter = painterResource(IconRes.drawable.ic_arrow_up),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // Mobile / Desktop toggle
    Surface(
        shape = AppShape(4.dp),
        color = if (isMobileView) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onMobileToggle(!isMobileView) }
    ) {
        Text(
            text = stringResource(if (isMobileView) Res.string.mobile_view else Res.string.desktop_view),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

/** Walk up the class hierarchy to find a declared method and make it accessible. */
internal fun findMethod(obj: Any, name: String, vararg paramTypes: Class<*>): java.lang.reflect.Method? {
    var c: Class<*>? = obj.javaClass
    while (c != null) {
        try {
            val m = c.getDeclaredMethod(name, *paramTypes)
            m.isAccessible = true
            return m
        } catch (_: NoSuchMethodException) { c = c.superclass }
    }
    return null
}

/** Prepend https:// if the user forgot the scheme. */
internal fun normaliseUrl(raw: String): String {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.isNotBlank() -> "https://$trimmed"
        else -> trimmed
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Type-to-page JS helpers.
//
// Rationale: CefBrowser.sendKeyEvent routes through the native OS event system
// (Win32 message queue / NSEvent / X11), which only accepts synthesised keys on
// Windows. On macOS/Linux keystrokes injected from the main window never reach
// the live browser on the secondary display because that window isn't the OS
// "key window". CefBrowser.executeJavaScript runs Chromium's in-process JS
// engine and therefore works identically on all three platforms.
//
// Limitation: the injected edits target document.activeElement. Pages using
// <canvas>-based editors (Google Docs, Figma) that don't use real DOM inputs
// won't receive characters this way — they need raw keystrokes we cannot
// cross-window forward. The WEB_JS_FOCUS_FIRST_INPUT helper covers the common case.
// ─────────────────────────────────────────────────────────────────────────────

internal fun commonPrefixLength(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return i
}

/** Encode a Kotlin [Char] as a JSON string literal, safe to splice into JS. */
private fun jsEncode(ch: Char): String = buildString {
    append('"')
    when (ch) {
        '\\' -> append("\\\\")
        '"'  -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (ch.code < FIRST_PRINTABLE_CHAR) append("\\u%04x".format(ch.code)) else append(ch)
    }
    append('"')
}

internal fun jsInsert(ch: Char): String = """
    (function(ch){
      var el=document.activeElement; if(!el) return;
      if (el.isContentEditable) { document.execCommand('insertText', false, ch); return; }
      if (el.tagName==='INPUT' || el.tagName==='TEXTAREA') {
        var s = el.selectionStart != null ? el.selectionStart : el.value.length;
        var e = el.selectionEnd   != null ? el.selectionEnd   : el.value.length;
        el.setRangeText(ch, s, e, 'end');
        el.dispatchEvent(new InputEvent('input', {data: ch, inputType: 'insertText', bubbles: true}));
      }
    })(${jsEncode(ch)});
""".trimIndent()

internal const val WEB_JS_BACKSPACE = """
    (function(){
      var el=document.activeElement; if(!el) return;
      if (el.isContentEditable) { document.execCommand('delete', false); return; }
      if (el.tagName==='INPUT' || el.tagName==='TEXTAREA') {
        var s=el.selectionStart, e=el.selectionEnd;
        if (s===e && s>0) { el.setRangeText('', s-1, s, 'end'); }
        else              { el.setRangeText('', s,   e, 'end'); }
        el.dispatchEvent(new InputEvent('input', {inputType: 'deleteContentBackward', bubbles: true}));
      }
    })();
"""

internal const val WEB_JS_ENTER = """
    (function(){
      var el=document.activeElement; if(!el) return;
      var down = new KeyboardEvent(
          'keydown',
          {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true},
      );
      var cancelled = !el.dispatchEvent(down);
      el.dispatchEvent(
          new KeyboardEvent('keyup', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true, cancelable:true}),
      );
      if (!cancelled && el.form) {
        if (el.form.requestSubmit) el.form.requestSubmit();
        else el.form.submit();
      }
    })();
"""

internal const val WEB_JS_FOCUS_FIRST_INPUT = """
    (function(){
      var el=document.querySelector('input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=reset]),textarea,[contenteditable=true]');
      if (el) el.focus();
    })();
"""
