@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_folder
import org.churchpresenter.icons.generated.resources.ic_refresh
import org.churchpresenter.icons.generated.resources.ic_search
import org.churchpresenter.icons.generated.resources.ic_settings
import org.churchpresenter.icons.generated.resources.ic_storage_pictures
import org.churchpresenter.icons.generated.resources.ic_storage_presentation
import org.churchpresenter.icons.generated.resources.ic_storage_songs
import org.churchpresenter.icons.generated.resources.ic_warning_outline
import org.churchpresenter.sharedui.composables.EmptyStateButton
import org.churchpresenter.sharedui.composables.EmptyStateContent
import org.churchpresenter.sharedui.composables.EmptyStatePanel
import org.churchpresenter.sharedui.composables.EmptyStateSecondaryButton
import org.churchpresenter.sharedui.composables.FileDrop
import org.churchpresenter.sharedui.composables.FormatChips
import kotlin.test.Test

class EmptyStateScreenshotTest {

    @Test
    fun `something to set up, with two actions and a hint`() = captureComponent(SECTION, "start") {
        Box360 {
            EmptyStateContent(
                icon = Res.drawable.ic_storage_songs,
                neutral = false,
                title = "No song library yet",
                body = "Choose the folder with your song database file.",
                hint = "or drag a folder here",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmptyStateButton("Choose folder", Res.drawable.ic_folder) {}
                    EmptyStateSecondaryButton("Open Settings", Res.drawable.ic_settings) {}
                }
            }
        }
    }

    @Test
    fun `nothing here right now, compact`() = captureComponent(SECTION, "neutral_compact") {
        Box360 {
            EmptyStateContent(
                icon = Res.drawable.ic_search,
                neutral = true,
                compact = true,
                title = "No songs match “amazng”",
                body = "Check the spelling, or search the lyrics instead.",
            ) {
                EmptyStateButton("Clear search", Res.drawable.ic_close) {}
            }
        }
    }

    @Test
    fun `something went wrong`() = captureComponent(SECTION, "error") {
        Box360 {
            EmptyStateContent(
                icon = Res.drawable.ic_warning_outline,
                neutral = false,
                error = true,
                title = "Can’t open this folder",
                body = "ChurchPresenter doesn’t have permission to read “Sunday photos”.",
            ) {
                EmptyStateButton("Try again", Res.drawable.ic_refresh) {}
            }
        }
    }

    @Test
    fun `something just worked`() = captureComponent(SECTION, "success") {
        Box360 {
            EmptyStateContent(
                icon = Res.drawable.ic_storage_songs,
                neutral = false,
                success = true,
                title = "1,248 songs loaded",
                body = "Pick a song from the list, or search for one, to see its lyrics.",
            )
        }
    }

    @Test
    fun `formats listed under the action`() = captureComponent(SECTION, "format_chips") {
        Box360 {
            EmptyStateContent(
                icon = Res.drawable.ic_storage_presentation,
                neutral = false,
                title = "No presentation open",
                body = "Open a PowerPoint, Keynote or PDF file.",
                hint = "or drag a file here",
                footer = { FormatChips(listOf(".pptx", ".ppt", ".key", ".pdf")) },
            ) {
                EmptyStateButton("Open presentation", Res.drawable.ic_folder) {}
            }
        }
    }

    @Test
    fun `a file dragged over the panel`() = captureComponent(SECTION, "drag_over") {
        val drop = FileDrop {}.apply { dragOver = true }
        EmptyStatePanel(
            drop,
            dropTitle = "Drop to add the folder",
            dropBody = "Its pictures will show here as slides.",
            modifier = Modifier.size(360.dp, 260.dp),
        ) {
            EmptyStateContent(
                icon = Res.drawable.ic_storage_pictures,
                neutral = false,
                title = "No folder selected",
                body = "Not shown while dragging.",
            )
        }
    }

    @Test
    fun `the panel at rest`() = captureComponent(SECTION, "panel") {
        EmptyStatePanel(FileDrop {}, "Drop to add the folder", "", Modifier.size(360.dp, 260.dp)) {
            EmptyStateContent(
                icon = Res.drawable.ic_storage_pictures,
                neutral = false,
                title = "No folder selected",
                body = "Choose a folder of pictures to show them as slides.",
            )
        }
    }

    private companion object {
        const val SECTION = "emptyState"
    }
}

@Composable
private fun Box360(content: @Composable () -> Unit) {
    Box(Modifier.width(420.dp)) { content() }
}
