package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.settings.utils.Constants

@Serializable
data class BackgroundSettings(
    val defaultBackgroundColor: String = "#000000",
    val defaultBackgroundImage: String = "",
    val defaultBackgroundVideo: String = "",
    val defaultBackgroundType: String = Constants.BACKGROUND_COLOR,
    val defaultLowerThirdBackgroundColor: String = "#000000",
    val defaultLowerThirdBackgroundImage: String = "",
    val defaultLowerThirdBackgroundVideo: String = "",
    val defaultBackgroundOpacity: Float = 1.0f,
    val defaultLowerThirdBackgroundType: String = Constants.BACKGROUND_COLOR,
    val defaultLowerThirdBackgroundOpacity: Float = 1.0f,
    val bibleBackground: BackgroundConfig = BackgroundConfig(),
    val bibleLowerThirdBackground: BackgroundConfig = BackgroundConfig(),
    val songBackground: BackgroundConfig = BackgroundConfig(),
    val songLowerThirdBackground: BackgroundConfig = BackgroundConfig(),
    /**
     * Dim and blur for the two Default cards, which spell their background out in flat fields
     * rather than carrying a [BackgroundConfig]. Appended rather than filed beside their opacity
     * siblings above: these are all same-typed parameters, so re-ordering them would silently
     * change the meaning of any positional construction.
     */
    val defaultBackgroundDim: Int = 0,
    val defaultBackgroundBlur: Int = 0,
    val defaultLowerThirdBackgroundDim: Int = 0,
    val defaultLowerThirdBackgroundBlur: Int = 0,
    /**
     * The quick tray's live pick, standing in front of every background above it — and in front of
     * a song's own, since an operator reaching for the tray mid-service is overriding what is on
     * screen right now.
     *
     * `@Transient`, because a pick is a live control and not a setting: it is set on the copy of
     * [AppSettings] the outputs render from, never written to `settings.json`, and it is gone when
     * the app restarts. The same shape `withMirroredBackgrounds` already uses for Instance Link.
     * Null is the ordinary case — nothing picked, everything above applies as configured.
     */
    @Transient val quickBackground: SongBackground? = null,
    @Transient val quickLowerThirdBackground: SongBackground? = null
)
