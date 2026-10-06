package org.churchpresenter.liveoutput

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.churchpresenter.settings.StageMonitorZoneStyle
import org.churchpresenter.settings.utils.Constants
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StageZoneHelpersTest {

    @Test
    fun `the clock reads 24-hour or 12-hour as asked`() {
        val time = LocalTime.of(15, 4, 5)
        assertEquals("15:04:05", formatClock(time, use24Hour = true))
        assertEquals("03:04:05", formatClock(time, use24Hour = false).take(8))
    }

    @Test
    fun `a zone's text lines up as its alignment says`() {
        assertEquals(TextAlign.Start, resolveTextAlign(Constants.LEFT))
        assertEquals(TextAlign.End, resolveTextAlign(Constants.RIGHT))
        assertEquals(TextAlign.Center, resolveTextAlign(Constants.CENTER))
    }

    @Test
    fun `a zone's column stacks from the side its alignment names`() {
        assertEquals(Arrangement.Bottom, resolveColumnVerticalArrangement(Constants.BOTTOM))
        assertEquals(Arrangement.Center, resolveColumnVerticalArrangement(Constants.MIDDLE))
        assertEquals(Arrangement.Top, resolveColumnVerticalArrangement(Constants.TOP))
        assertEquals(Alignment.End, resolveColumnHorizontalAlignment(Constants.RIGHT))
        assertEquals(Alignment.CenterHorizontally, resolveColumnHorizontalAlignment(Constants.CENTER))
        assertEquals(Alignment.Start, resolveColumnHorizontalAlignment(Constants.LEFT))
    }

    @Test
    fun `a zone's content sits in the corner its alignment names`() {
        val bottomRight = StageMonitorZoneStyle(
            verticalAlignment = Constants.BOTTOM,
            horizontalAlignment = Constants.RIGHT,
        )
        val middleCenter = StageMonitorZoneStyle(
            verticalAlignment = Constants.MIDDLE,
            horizontalAlignment = Constants.CENTER,
        )
        val topLeft = StageMonitorZoneStyle(verticalAlignment = Constants.TOP, horizontalAlignment = Constants.LEFT)
        assertEquals(BiasAlignment(1f, 1f), zoneContentAlignment(bottomRight))
        assertEquals(BiasAlignment(0f, 0f), zoneContentAlignment(middleCenter))
        assertEquals(BiasAlignment(-1f, -1f), zoneContentAlignment(topLeft))
    }

    @Test
    fun `a plain style is upright, regular and unshadowed`() {
        val style = buildTextStyle(fontType = "Arial", fontSize = 24, color = Color.White)
        assertEquals(24.sp, style.fontSize)
        assertEquals(FontWeight.Normal, style.fontWeight)
        assertEquals(FontStyle.Normal, style.fontStyle)
        assertEquals(TextDecoration.None, style.textDecoration)
        assertNull(style.shadow)
    }
}
