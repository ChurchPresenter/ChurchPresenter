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

    /**
     * An explicit skiko render API, overriding whatever this platform would otherwise pick.
     * Set via env var CHURCHPRESENTER_RENDER_API (no JVM flag knowledge needed) or the
     * -Dchurchpresenter.renderApi system property; the value is skiko's own — METAL, DIRECT3D,
     * OPENGL or SOFTWARE.
     *
     * It exists so a machine whose GPU driver fares worse on the platform default has a way out
     * without waiting for a build. Read once, before the first SkiaLayer is created; skiko latches
     * the property on first use, so setting it later does nothing.
     */
    val renderApiOverride: String? by lazy {
        System.getenv("CHURCHPRESENTER_RENDER_API")
            ?: System.getProperty("churchpresenter.renderApi")
    }
}
