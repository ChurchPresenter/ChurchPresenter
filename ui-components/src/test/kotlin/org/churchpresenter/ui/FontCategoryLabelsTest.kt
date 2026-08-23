package org.churchpresenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each font category's label in the picker.
 *
 * The four are a `when` over an enum, so a swapped pair reads perfectly and mislabels every family
 * in two of the four groups — which is only visible if all four are asked for at once.
 */
@OptIn(ExperimentalTestApi::class)
class FontCategoryLabelsTest {

    @Test
    fun `every category has its own label`() = runComposeUiTest {
        val labels = mutableMapOf<FontCategory, String>()
        setContent {
            MaterialTheme {
                Column {
                    FontCategory.entries.forEach { category ->
                        labels[category] = categoryLabel(category)
                        Text(labels.getValue(category))
                    }
                }
            }
        }

        assertEquals(
            FontCategory.entries.size,
            labels.values.toSet().size,
            "no two categories may share a label, got $labels",
        )
        assertEquals(FontCategory.entries.toSet(), labels.keys, "all four are labelled")
    }
}
