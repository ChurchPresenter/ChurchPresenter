@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.churchpresenter.ui.DropdownSelector
import kotlin.test.Test

class DropdownSelectorScreenshotTest {

    private val box = Modifier.width(200.dp)

    private val scopes = listOf(
        "all" to "Entire Bible",
        "book" to "Current Book",
        "chapter" to "Current Chapter",
    )

    @Test
    fun closed() = captureComponent(SECTION, "closed") {
        DropdownSelector(label = "SCOPE", value = "all", options = scopes, onValueChange = {}, modifier = box)
    }

    @Test
    fun open() = captureComponent(
        SECTION,
        "open",
        rootIndex = 1,
        drive = {
            onNodeWithText("Entire Bible").performClick()
            waitForIdle()
        },
    ) {
        DropdownSelector(label = "SCOPE", value = "all", options = scopes, onValueChange = {}, modifier = box)
    }

    @Test
    fun compact() = captureComponent(SECTION, "compact") {
        DropdownSelector(
            label = "MODE",
            value = "all",
            options = scopes,
            onValueChange = {},
            modifier = box,
            compact = true,
        )
    }

    @Test
    fun `with trailing content on each item`() = captureComponent(
        SECTION,
        "open_with_trailing_content",
        rootIndex = 1,
        drive = {
            onNodeWithText("Entire Bible").performClick()
            waitForIdle()
        },
    ) {
        DropdownSelector(
            label = "SCOPE",
            value = "all",
            options = scopes,
            onValueChange = {},
            modifier = box,
            itemTrailingContent = { _, index ->
                Modifier.size(10.dp).let { modifier ->
                    Box(
                        modifier
                            .background(
                                if (index == 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                    )
                }
            },
        )
    }

    @Test
    fun `the plain string overload`() = captureComponent(SECTION, "string_overload") {
        DropdownSelector(
            label = "TRANSLATION",
            items = listOf("KJV", "RST", "NIV"),
            selected = "KJV",
            onSelectedChange = {},
        )
    }

    @Test
    fun `the plain string overload, open`() = captureComponent(
        SECTION,
        "string_overload_open",
        rootIndex = 1,
        drive = {
            onNodeWithText("KJV").performClick()
            waitForIdle()
        },
    ) {
        DropdownSelector(
            label = "TRANSLATION",
            items = listOf("KJV", "RST", "NIV"),
            selected = "KJV",
            onSelectedChange = {},
        )
    }

    private companion object {
        const val SECTION = "dropdownSelector"
    }
}
