@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.app.churchpresenter.dialogs.ContactUsDialogContent
import org.churchpresenter.app.churchpresenter.dialogs.SendStatus
import org.churchpresenter.theme.ChurchPresenterTheme
import java.io.File
import kotlin.test.Test

class AppPreviewContactScreenshotTest {

    @Test
    fun `contact us`() {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        THEMES.forEach { (suffix, mode) ->
            runSkikoComposeUiTest(size = Size(520f, 660f), density = Density(1f)) {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Box(Modifier.size(520.dp, 660.dp)) {
                            ContactUsDialogContent(
                                onDismiss = {},
                                types = TYPES,
                                selectedType = TYPES[1],
                                onSelectedTypeChange = {},
                                name = "Sarah Bennett",
                                onNameChange = {},
                                email = "sarah@gracechurch.org",
                                onMessageChange = {},
                                message = "Thank you for building this — we've used it every Sunday " +
                                    "since Easter and the lower thirds have made a real difference " +
                                    "to our livestream.",
                                onEmailChange = {},
                                status = SendStatus.Idle,
                                onSend = {},
                                sentText = "",
                            )
                        }
                    }
                }
                waitForIdle()
                captureTo(File("$SCREENSHOT_ROOT/previewApp/contact_$suffix.png"))
            }
        }
    }

    private companion object {
        val TYPES = listOf(
            "Feature Request" to "featureRequest",
            "Feedback" to "feedback",
            "Testimonial" to "testimonial",
            "Bug Report" to "bug",
        )
    }
}
