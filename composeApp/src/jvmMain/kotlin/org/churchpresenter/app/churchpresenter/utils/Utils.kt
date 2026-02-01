package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import java.awt.GraphicsEnvironment

object Utils {

    fun getAvailableSystemFonts(): List<String> {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }

    @OptIn(ExperimentalTextApi::class)
    fun systemFontFamilyOrDefault(fontName: String): FontFamily {
        return try {
            val name = GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts.first { it.name == fontName }.name
            FontFamily(name)
        } catch (e: Exception) {
            FontFamily.Default
        }
    }
}