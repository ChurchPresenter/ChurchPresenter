package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

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
    val songLowerThirdBackground: BackgroundConfig = BackgroundConfig()
)
