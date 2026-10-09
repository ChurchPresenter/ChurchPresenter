@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/** The Bible catalog's round icon badge. */
class IconBadgeTest {

    @Test
    fun `a badge takes the size it is given`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                IconBadge(
                    icon = Icons.Filled.Book,
                    modifier = Modifier.testTag("badge"),
                    container = Color.Black,
                    content = Color.White,
                    size = 24.dp,
                )
            }
        }

        onNodeWithTag("badge").assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
    }
}
