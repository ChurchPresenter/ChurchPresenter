package org.churchpresenter.app.churchpresenter.utils

object DevFlags {
    /**
     * Forces the dev-window presenter fallback on, even in a release build.
     * Set via env var CHURCHPRESENTER_FORCE_DEV_WINDOW (no JVM flag knowledge needed) or the
     * -Dchurchpresenter.forceDevWindow system property. Never affects BuildConfig.IS_RELEASE
     * or any analytics/crash-reporting behavior that keys off it.
     */
    val forceDevWindow: Boolean by lazy {
        System.getenv("CHURCHPRESENTER_FORCE_DEV_WINDOW")?.toBoolean()
            ?: System.getProperty("churchpresenter.forceDevWindow")?.toBoolean()
            ?: false
    }
}
