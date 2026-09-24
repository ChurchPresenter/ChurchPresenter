@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Duplicate and Delete keys on each profile row are square.
 *
 * They were sized by width alone, and the key they sit in is 40dp tall by default, so they came out
 * as tall narrow slots with their icons squashed to match — the restyle's raised key made the shape
 * plain to see.
 */
class ProfileRailKeysTest {

    @Test
    fun `the duplicate and delete keys are square`() = profilesTab(profileDocument()) { _ ->
        listOf("Duplicate", "Delete").forEach { label ->
            val bounds = onAllNodes(hasContentDescription(label)).onFirst().getBoundsInRoot()
            val width = (bounds.right - bounds.left).value
            val height = (bounds.bottom - bounds.top).value
            assertTrue(abs(width - height) <= 0.5f, "$label key is ${width}x$height, not square")
        }
    }
}
