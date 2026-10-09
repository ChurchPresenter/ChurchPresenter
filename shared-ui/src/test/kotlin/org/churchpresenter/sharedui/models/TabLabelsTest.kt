package org.churchpresenter.sharedui.models

import kotlin.test.Test
import kotlin.test.assertEquals

class TabLabelsTest {

    @Test
    fun `every tab has a label of its own`() {
        val labels = Tabs.entries.map { it.labelRes.key }
        assertEquals(labels.size, labels.toSet().size)
    }
}
