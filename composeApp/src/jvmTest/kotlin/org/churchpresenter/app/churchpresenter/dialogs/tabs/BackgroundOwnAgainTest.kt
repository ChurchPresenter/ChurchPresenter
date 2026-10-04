package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundOwnAgainTest {

    @Test
    fun `taking over from a default background starts from a plain color`() {
        val fromDefault = BackgroundConfig().ownAgain(BackgroundConfig(backgroundType = Constants.BACKGROUND_DEFAULT))
        val fromFollow =
            BackgroundConfig().ownAgain(BackgroundConfig(backgroundType = Constants.BACKGROUND_FOLLOW_DEFAULT))

        assertEquals(Constants.BACKGROUND_COLOR, fromDefault.backgroundType)
        assertEquals(Constants.BACKGROUND_COLOR, fromFollow.backgroundType)
    }

    @Test
    fun `taking over from a picture keeps the picture`() {
        val shown = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = "/stills/dawn.png")

        val own = BackgroundConfig().ownAgain(shown)

        assertEquals(Constants.BACKGROUND_IMAGE, own.backgroundType)
        assertEquals("/stills/dawn.png", own.backgroundImage)
    }
}
