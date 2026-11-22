package org.churchpresenter.app.churchpresenter

import org.jetbrains.skiko.SkiaLayer
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull

class OutputGuardCaptureTest {

    @BeforeTest
    fun setUp() {
        TestSingletons.latchSkikoHostOs()
    }

    @Test
    fun `an output whose Skia surface has drawn nothing yet gives no still`() {
        var still: Still? = null
        SwingUtilities.invokeAndWait {
            val layer = SkiaLayer()
            try {
                still = captureStill(JPanel().apply { add(JPanel().apply { add(layer) }) })
            } finally {
                layer.dispose()
            }
        }
        assertNull(still)
    }
}
