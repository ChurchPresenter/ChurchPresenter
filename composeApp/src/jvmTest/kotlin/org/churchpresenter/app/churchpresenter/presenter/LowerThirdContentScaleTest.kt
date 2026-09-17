package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.layout.ContentScale
import kotlin.test.Test
import kotlin.test.assertEquals

class LowerThirdContentScaleTest {

    @Test
    fun `portrait canvas crops`() {
        assertEquals(ContentScale.Crop, lowerThirdContentScale(400f / 800f))
    }

    @Test
    fun `landscape canvas fits`() {
        assertEquals(ContentScale.Fit, lowerThirdContentScale(1920f / 1080f))
    }

    @Test
    fun `square canvas fits`() {
        assertEquals(ContentScale.Fit, lowerThirdContentScale(1f))
    }

    @Test
    fun `just under square crops`() {
        assertEquals(ContentScale.Crop, lowerThirdContentScale(0.999f))
    }
}
