package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

@Serializable
data class AnnouncementsSettings(
    val text: String = "",
    val textColor: String = "#FFFFFF",
    val backgroundColor: String = "#00000000",
    val fontSize: Int = 48,
    val fontType: String = "Arial",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 78,
    val horizontalAlignment: String = Constants.CENTER,
    val position: String = Constants.CENTER,
    val animationType: String = Constants.ANIMATION_SLIDE_FROM_BOTTOM,
    val animationDuration: Int = 12000,
    val loopCount: Int = 0,
    val timerHours: Int = 0,
    val timerMinutes: Int = 0,
    val timerSeconds: Int = 0,
    val timerTextColor: String = "#FFFFFF",
    val timerExpiredText: String = "",
    val timerMode: String = Constants.TIMER_MODE_DURATION,
    val targetHour: Int = 0,
    val targetMinute: Int = 0,
    val targetSecond: Int = 0
)
