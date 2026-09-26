@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTranslation
import org.churchpresenter.songlibrary.ui.COMPARE_WINDOW_HEIGHT
import org.churchpresenter.songlibrary.ui.COMPARE_WINDOW_WIDTH
import org.churchpresenter.songlibrary.ui.CompareTranslationsContent
import org.churchpresenter.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The Compare Translations window the Song Library Manager opens from a row, in both themes.
 *
 * Shot through `CompareTranslationsContent` rather than the `DialogWindow` around it, which a
 * headless test cannot photograph, at the window's own size so what is reviewed is what opens.
 */
class CompareTranslationsScreenshotTest {

    /** Four languages: verse 1 lines up, verses 2 and 3 are a line out, verse 4 is missing in one. */
    @Test
    fun `a song with problems`() = shoot("problems")

    @Test
    fun `only the sections with problems`() = shoot("only_problems") { click("Only problems") }

    @Test
    fun `one language hidden`() = shoot("language_hidden") { click("Українська") }

    /** The short verse fixed in place, which turns Done into Save Changes. */
    @Test
    fun `a section edited`() = shoot("edited") {
        // Verse 3 in Russian: the third card's second cell.
        onAllNodes(hasSetTextAction())[CARD_CELLS * 2 + 1].performTextReplacement(RU_VERSE_3_FIXED)
        waitForIdle()
    }

    @Test
    fun `two languages that line up`() = shoot("all_lined_up", song = BILINGUAL)

    private fun shoot(name: String, song: SongItem = GRACE, drive: ComposeUiTest.() -> Unit = {}) =
        stackedThemes(SECTION, name) { mode, file ->
            val width = COMPARE_WINDOW_WIDTH.value.toInt()
            val height = COMPARE_WINDOW_HEIGHT.value.toInt()
            runDesktopComposeUiTest(width = width, height = height) {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        CompareTranslationsContent(song = song, onDismiss = {}, onSave = {})
                    }
                }
                waitForIdle()
                drive()
                captureTo(file)
            }
        }

    private fun ComposeUiTest.click(text: String) {
        onAllNodesWithText(text)[0].performClick()
        waitForIdle()
    }

    private companion object {
        const val SECTION = "compareTranslations"
        const val CARD_CELLS = 4

        /** Each verse given as its lines, headed `[Verse n]` and set apart by a blank line. */
        fun verses(vararg verses: List<String>): List<String> =
            verses.flatMapIndexed { index, lines ->
                (if (index > 0) listOf("") else emptyList()) + "[Verse ${index + 1}]" + lines
            }

        fun language(label: String, title: String, lyrics: List<String>) =
            SongTranslation(label = label, title = title, lyrics = lyrics)

        val RU_VERSE_3_FIXED = listOf(
            "Сквозь много бед и много зла",
            "Я ныне прохожу;",
            "И благодать меня вела",
            "Домой, где я найду.",
        ).joinToString("\n")

        val GRACE = SongItem(
            number = "001",
            title = "Amazing Grace",
            songbook = "Hymnal",
            lyrics = verses(
                listOf(
                    "Amazing grace! How sweet the sound",
                    "That saved a wretch like me!",
                    "I once was lost, but now am found;",
                    "Was blind, but now I see.",
                ),
                listOf(
                    "'Twas grace that taught my heart to fear,",
                    "And grace my fears relieved;",
                    "How precious did that grace appear",
                    "The hour I first believed.",
                ),
                listOf(
                    "Through many dangers, toils and snares,",
                    "I have already come;",
                    "'Tis grace hath brought me safe thus far,",
                    "And grace will lead me home.",
                ),
                listOf(
                    "When we've been there ten thousand years,",
                    "Bright shining as the sun,",
                    "We've no less days to sing God's praise",
                    "Than when we'd first begun.",
                ),
            ),
        ).withTranslations(
            listOf(
                language(
                    "Русский", "О благодать",
                    verses(
                        listOf(
                            "О благодать, спасён тобой",
                            "Я из пучины бед;",
                            "Был мёртв и чудом стал живой,",
                            "Был слеп и вижу свет.",
                        ),
                        listOf(
                            "Меня учила ты страшить",
                            "И страх мой прогнала;",
                            "Как драгоценно было жить,",
                            "Когда ты в сердце вошла.",
                        ),
                        listOf(
                            "Сквозь много бед и много зла",
                            "Я ныне прохожу;",
                            "Домой ты вновь меня вела.",
                        ),
                        listOf(
                            "Пройдут и тысячи веков,",
                            "Как солнце мы горим;",
                            "И Богу славу вновь и вновь",
                            "Мы в вечности творим.",
                        ),
                    ),
                ),
                language(
                    "Українська", "О благодать",
                    verses(
                        listOf(
                            "О благодать, мене знайшла,",
                            "Спасла з глибин гріха;",
                            "Був я загублений — прийшла,",
                            "І світло осіня.",
                        ),
                        listOf(
                            "Навчила серце шанувать",
                            "І страх мій забрала;",
                            "Як дорого було пізнать",
                            "Той час, коли прийшла.",
                        ),
                        listOf(
                            "Крізь небезпеки і труди",
                            "Я вже сюди дійшов;",
                            "Вела мене вона завжди",
                            "І поведе ізнов.",
                        ),
                    ),
                ),
                language(
                    "Кыргызча", "Оо ырайым",
                    verses(
                        listOf(
                            "Оо ырайым, кандай таттуу үн,",
                            "Мени куткарган сен;",
                            "Адашкан элем, таптың бүгүн,",
                            "Сокур элем, көрдүм мен.",
                        ),
                        listOf(
                            "Жүрөгүмө коркууну үйрөттүң,",
                            "Коркунучту алып салдың;",
                            "Кандай баалуу болду ошол күн,",
                            "Качан мен ишендим,",
                            "Качан сен келдиң.",
                        ),
                        listOf(
                            "Көп коркунуч, эмгек, тузак",
                            "Аркылуу мен келдим;",
                            "Ырайым мени сактап узак,",
                            "Үйгө алып барат дедим.",
                        ),
                        listOf(
                            "Он миң жыл ал жакта болсок,",
                            "Күндөй жаркырап биз;",
                            "Кудайды даңктап ырдайбыз",
                            "Баштагыдай эле биз.",
                        ),
                    ),
                ),
            ),
        )

        val BILINGUAL = SongItem(
            number = "002",
            title = "Silent Night",
            songbook = "Christmas/Carols",
            lyrics = verses(
                listOf("Silent night, holy night,", "All is calm, all is bright"),
                listOf("Shepherds quake", "At the sight"),
            ),
        ).withTranslations(
            listOf(
                language(
                    "Русский", "Тихая ночь",
                    verses(
                        listOf("Тихая ночь, дивная ночь,", "Всё вокруг спит"),
                        listOf("Пастухи", "Видят свет"),
                    ),
                ),
            ),
        )
    }
}
