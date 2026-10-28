@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.signage.tab

import org.churchpresenter.theme.FocusVisibility
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import org.churchpresenter.theme.LocalFocusVisibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.sharedui.testing.FakeFileChooser
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO

/** What the tab asked of the computer, answered without touching it. */
class FakeDesktop(chosen: Path? = null, var deleted: Boolean = true, var trash: Boolean = true) {
    val chooser = FakeFileChooser(chosen)
    val opened = mutableListOf<Path>()
    val deletes = mutableListOf<Pair<Path, String>>()
    val desktop = SignageDesktop(
        fileChooser = chooser,
        openFolder = { opened.add(it) },
        deletePlaylist = { root, id ->
            deletes.add(root to id)
            deleted
        },
        canTrash = { trash },
    )
}

/** Composes the Signage tab over [world] and runs [block]. */
fun signageTab(
    world: SignageWorld,
    desktop: FakeDesktop = FakeDesktop(),
    serverRunning: Boolean = true,
    onTurnOnServer: () -> Unit = {},
    block: ComposeUiTest.() -> Unit,
) = runComposeUiTest {
    setContent {
        // The tab inside a window where Tab has been used, as it is once someone tabs into it from the
        // tab bar: focus rings show from the first stop.
        val tabbed = remember { FocusVisibility().apply { afterTab = true } }
        CompositionLocalProvider(LocalFocusVisibility provides tabbed) {
            ChurchPresenterTheme(ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SignageTab(world.controller, serverRunning, onTurnOnServer, desktop = desktop.desktop)
                }
            }
        }
    }
    block()
}

/** A small black JPEG in base64, as a player sends for a preview. */
fun jpegBase64(width: Int = 480, height: Int = 270): String {
    val out = ByteArrayOutputStream()
    ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg", out)
    return Base64.getEncoder().encodeToString(out.toByteArray())
}
