@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import org.churchpresenter.profiles.BOOK_COUNT
import org.churchpresenter.profiles.BookNamesContent
import org.churchpresenter.profiles.BookNamesKey
import org.churchpresenter.profiles.BookNamesRead
import org.churchpresenter.profiles.BookNamesStatus
import org.churchpresenter.profiles.BookTitles
import org.churchpresenter.profiles.ModuleBookNames
import org.churchpresenter.sharedui.screenshot.captureComponent
import kotlin.test.Test

class BookNamesScreenshotTest {

    private val english = listOf(
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel",
    ).let { named -> (1..BOOK_COUNT).map { named.getOrElse(it - 1) { _ -> "Book name $it" } } }
    private val russian = listOf(
        "Бытие", "Исход", "Левит", "Числа", "Второзаконие", "Иисус Навин", "Судьи", "Руфь", "1 Царств", "2 Царств",
    ).let { named -> (1..BOOK_COUNT).map { named.getOrElse(it - 1) { _ -> "Книга $it" } } }
    private val titles = BookTitles(english, russian)

    private val allNames = ModuleBookNames(
        (1..BOOK_COUNT).associateWith { russian[it - 1] },
        BookNamesRead.ALL,
    )

    private fun shoot(
        name: String,
        module: ModuleBookNames,
        overrides: Map<Int, String> = emptyMap(),
        drive: ComposeUiTest.() -> Unit = {},
    ) = captureComponent(SECTION, name, drive = drive) {
        Box(Modifier.size(640.dp, 620.dp)) {
            BookNamesContent("Russian Synodal · RST", module, titles, overrides, {}, {})
        }
    }

    @Test
    fun `names from the file`() = shoot("from_file", allNames)

    @Test
    fun `some books overridden`() = shoot("overridden", allNames, mapOf(2 to "Шемот", 3 to "Ваикра"))

    @Test
    fun `some names could not be extracted`() = shoot(
        "some_names",
        ModuleBookNames((1..3).associateWith { russian[it - 1] }, BookNamesRead.SOME),
    )

    @Test
    fun `no names could be extracted`() = shoot("no_names", ModuleBookNames(emptyMap(), BookNamesRead.NONE))

    @Test
    fun `the file could not be read`() = shoot("unreadable", ModuleBookNames(emptyMap(), BookNamesRead.FAILED))

    @Test
    fun `only the overridden books listed`() = shoot("filter_overridden", allNames, mapOf(2 to "Шемот")) {
        onNodeWithText("Overridden  1").performClick()
        waitForIdle()
    }

    @Test
    fun `a search with no match`() = shoot("no_match", allNames) {
        onNodeWithContentDescription("Find a book").performTextReplacement("nowhere")
        waitForIdle()
    }

    @Test
    fun `the key in every status`() = captureComponent(SECTION, "key_statuses") {
        Column(Modifier.width(128.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                BookNamesStatus.FromFile,
                BookNamesStatus.Edited(3),
                BookNamesStatus.NotSet,
                BookNamesStatus.PartlySet(12),
                BookNamesStatus.Custom,
            ).forEach { BookNamesKey(it, onClick = {}, modifier = Modifier.width(128.dp)) }
        }
    }

    private companion object {
        const val SECTION = "bookNames"
    }
}
