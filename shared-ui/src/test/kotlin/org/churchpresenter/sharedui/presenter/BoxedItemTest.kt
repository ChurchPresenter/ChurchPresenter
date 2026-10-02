@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.sharedui.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.TextBoxOverflow
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

class BoxedItemTest {

    private fun spill(vertical: String) = TextBox(enabled = true, overflow = TextBoxOverflow.SPILL, vertical = vertical)

    private fun drawsIn(box: TextBox, horizontal: String) = runComposeUiTest {
        setContent {
            Box(Modifier.size(200.dp)) {
                BoxedItem(Rect(10f, 10f, 150f, 80f), box, horizontal, "key") { Text("Boxed") }
            }
        }
        onNodeWithText("Boxed").assertExists()
    }

    @Test
    fun `cut off text is clipped to its box`() =
        drawsIn(TextBox(enabled = true, overflow = TextBoxOverflow.CUT, vertical = Constants.TOP), Constants.LEFT)

    @Test
    fun `spilling text hangs from the top of its box`() =
        drawsIn(spill(Constants.TOP), Constants.RIGHT)

    @Test
    fun `spilling text rises from the bottom of its box`() =
        drawsIn(spill(Constants.BOTTOM), Constants.CENTER)

    @Test
    fun `spilling text grows both ways from the middle of its box`() =
        drawsIn(spill(Constants.MIDDLE), Constants.CENTER)

    @Test
    fun `shrinking text sits in its box`() =
        drawsIn(TextBox(enabled = true, overflow = TextBoxOverflow.SHRINK), Constants.CENTER)
}
