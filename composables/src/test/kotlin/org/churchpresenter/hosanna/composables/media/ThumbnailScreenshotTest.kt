package org.churchpresenter.hosanna.composables.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.model.ThumbnailFileLabels
import org.churchpresenter.hosanna.composables.model.ThumbnailLabels
import org.churchpresenter.hosanna.composables.model.ThumbnailSize
import org.churchpresenter.hosanna.composables.model.ThumbnailStatus
import org.churchpresenter.hosanna.composables.testing.frozenSource
import org.churchpresenter.hosanna.composables.testing.hosannaComponent
import org.churchpresenter.hosanna.composables.testing.testPortrait
import org.churchpresenter.hosanna.composables.testing.testSlide
import kotlin.test.Test

class ThumbnailScreenshotTest {

    private val section = "media/thumbnail"
    private val labels = ThumbnailLabels(
        "Go live", "Hide from show", "Show in the show again", "Hidden · skipped when presenting", "LIVE",
        ThumbnailFileLabels("File not found", "Locate…", "Offline"),
    )

    @Composable
    private fun Tile(
        caption: String,
        state: ButtonState = ButtonState.Rest,
        image: Painter? = testSlide(),
        size: ThumbnailSize = ThumbnailSize.Medium,
        status: ThumbnailStatus = ThumbnailStatus.Ready,
        selected: Boolean = false,
        live: Boolean = false,
        hidden: Boolean = false,
        duration: String? = null,
        progress: Float? = null,
    ) {
        Thumbnail(
            image, {}, labels,
            interactionSource = remember { frozenSource(state) },
            size = size, caption = caption, subtitle = "Sermon.pptx",
            selected = selected, live = live, number = "3", duration = duration, progress = progress,
            status = status, hidden = hidden,
            onGoLive = {}, onHiddenChange = {}, onLocate = {},
        )
    }

    @Composable
    private fun Grid(content: @Composable () -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) { content() }
    }

    @Test
    fun states() = hosannaComponent(section, "states") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Grid {
                Tile("Rest")
                Tile("Hovered", ButtonState.Hovered)
                Tile("Pressed", ButtonState.Pressed)
                Tile("Focused", ButtonState.Focused)
            }
            Grid {
                Tile("Selected", selected = true)
                Tile("Live", live = true)
                Tile("Live and selected", selected = true, live = true)
            }
        }
    }

    @Test
    fun statuses() = hosannaComponent(section, "statuses") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Grid {
                Tile("Loading", image = null, status = ThumbnailStatus.Loading)
                Tile("Missing", image = null, status = ThumbnailStatus.Missing)
                Tile("Missing, hovered", ButtonState.Hovered, image = null, status = ThumbnailStatus.Missing)
            }
            Grid {
                Tile("Offline", status = ThumbnailStatus.Offline)
                Tile("Hidden", hidden = true)
                Tile("Hidden, hovered", ButtonState.Hovered, hidden = true)
            }
        }
    }

    @Test
    fun badges() = hosannaComponent(section, "badges") {
        Grid {
            Tile("Video", duration = "3:34", progress = 0.4f)
            Tile("Portrait", image = testPortrait())
            Tile("42 Great Is Thy Faithfulness (with the extra verse)", live = true, duration = "4:05")
        }
    }

    @Test
    fun sizes() = hosannaComponent(section, "sizes") {
        Grid {
            ThumbnailSize.entries.forEach { size ->
                Tile(size.name, size = size, live = true, duration = "3:34")
            }
        }
    }
}
