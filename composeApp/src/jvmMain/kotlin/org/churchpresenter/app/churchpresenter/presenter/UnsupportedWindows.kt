package org.churchpresenter.app.churchpresenter.presenter

/** The `os.name` values of every Windows older than 10, desktop and server alike. */
private val UNSUPPORTED_WINDOWS = setOf(
    "Windows XP", "Windows Vista", "Windows 7", "Windows 8", "Windows 8.1",
    "Windows Server 2008", "Windows Server 2008 R2", "Windows Server 2012", "Windows Server 2012 R2",
)

/**
 * Whether the web engine cannot run on this Windows at all.
 *
 * Chromium 110 dropped Windows 7 and 8.1 (and their Server 2008 R2/2012 editions), so the CEF
 * bundled here cannot load there: `libcef.dll` fails with "The specified procedure could not be
 * found" (Sentry CHURCH-PRESENTER-DESKTOP-75, reported from Windows 8.1). Nothing on the machine
 * can repair that, so it is detected up front -- no engine download, no misleading report, and a
 * Web tab that names the real cause instead of pointing at the Visual C++ runtime.
 */
internal fun isUnsupportedWindowsForJcef(osName: String = System.getProperty("os.name", "")): Boolean =
    osName.trim() in UNSUPPORTED_WINDOWS
