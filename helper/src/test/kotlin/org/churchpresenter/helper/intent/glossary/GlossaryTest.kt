package org.churchpresenter.helper.intent.glossary

import org.churchpresenter.helper.intent.normalize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlossaryTest {

    private val words = Glossary(
        "xx",
        listOf(
            "фон*" to "background",
            "главн* экран*" to "main screen",
            "синий" to "blue",
            "песн*" to "song",
        ),
    )

    @Test
    fun `whole words and word starts are put into english`() {
        assertEquals("make background song blue", words.toEnglish(normalize("make фона песни синий")))
    }

    @Test
    fun `a star works on every word of a key`() {
        assertEquals("main screen", words.toEnglish(normalize("главный экрана")))
    }

    @Test
    fun `e with diaeresis reads as e`() {
        val yo = Glossary("xx", listOf("темно" to "dark"))
        assertEquals("dark", yo.toEnglish(normalize("тёмно")))
    }

    @Test
    fun `an unspaced language matches anywhere and keeps the rest`() {
        val chinese = Glossary("zh", listOf("背景" to "background", "蓝色" to "blue"), spaced = false)
        assertEquals("把 background 改成 blue", chinese.toEnglish(normalize("把背景改成蓝色")))
    }

    @Test
    fun `words a glossary does not know are left as typed`() {
        assertEquals("иоанна 3:16", words.toEnglish(normalize("Иоанна 3:16")))
    }

    @Test
    fun `other scripts' digits are read as numbers`() {
        assertEquals("5 5 5", normalize("5 ५ ๕"))
    }

    @Test
    fun `the app's own language is read first, then the text as typed`() {
        val readings = Glossaries.readings(normalize("фон синий"), "ru").toList()
        assertTrue(readings.first().startsWith("background"), "first reading: ${readings.first()}")
        assertEquals("фон синий", readings[1])
    }
}
