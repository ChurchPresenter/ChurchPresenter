@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEvent
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventDialogContent
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import org.churchpresenter.theme.ChurchPresenterTheme
import java.io.File
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.SCREENSHOT_ROOT
import org.churchpresenter.ui.screenshot.THEMES
import org.churchpresenter.ui.screenshot.captureTo

class AppPreviewRemotePermissionScreenshotTest {

    private fun permission(
        name: String,
        event: RemoteEvent,
        actionLabel: String,
        remaining: Int = 0,
        isClientKnownAllowed: Boolean = false,
    ) = THEMES.forEach { (suffix, mode) ->
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Box(
                        // The size the real DialogWindow opens at (DialogSizes.kt); without a
                        // height the content stretches and leaves a gap the operator never sees.
                        //
                        // No backdrop behind it, and no padding around it: this shot is exported to
                        // the website, which frames it itself. A painted margin inside the image
                        // fought that frame — 24dp of surfaceVariant on all four sides, invisible
                        // against the dark page and an obvious grey slab against the light one.
                        // Safe to sit flush because the content is an opaque Surface with no shape
                        // (RemoteEventDialog.kt), so there are no rounded corners to expose.
                        Modifier.size(500.dp, 290.dp)
                    ) {
                        RemoteEventDialogContent(
                            event = event,
                            actionLabel = actionLabel,
                            typeIcon = Icons.Filled.CalendarMonth,
                            typeAccent = MaterialTheme.colorScheme.primary,
                            bodyTitle = event.title,
                            remaining = remaining,
                            showAllowPermanently = true,
                            isClientKnownAllowed = isClientKnownAllowed,
                            isClientKnownBlocked = false,
                            isInstanceLinkFollower = false,
                            onAllow = {},
                            onAllowForSession = {},
                            onAllowPermanently = {},
                            onBlockForSession = {},
                            onBlockPermanently = {},
                            onDeny = {},
                        )
                    }
                }
            }
            waitForIdle()
            captureTo(File("$SCREENSHOT_ROOT/previewApp/permission_${name}_$suffix.png"))
        }
    }

    @Test
    fun `adding a bible verse to the schedule`() = permission(
        name = "add_bible_verse",
        event = RemoteEvent(
            type = RemoteEventType.ADD_TO_SCHEDULE,
            title = "Psalm 23:1-3",
            detail = "The LORD is my shepherd; I shall not want.",
            clientId = "a4f19c72",
            clientLabel = "Sound desk iPad",
        ),
        actionLabel = "Add to Schedule",
    )
}
