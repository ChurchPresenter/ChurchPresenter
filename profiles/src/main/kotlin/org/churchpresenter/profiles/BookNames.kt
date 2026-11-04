package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.isPlaceholderBookName
import java.io.File

internal const val BOOK_COUNT = 66
internal const val OLD_TESTAMENT_BOOKS = 39

internal enum class BookNameFilter { ALL, OLD_TESTAMENT, NEW_TESTAMENT, OVERRIDDEN }

internal sealed interface BookNamesStatus {
    data object FromFile : BookNamesStatus
    data class Edited(val count: Int) : BookNamesStatus
    data object NotSet : BookNamesStatus
    data class PartlySet(val count: Int) : BookNamesStatus
    data object Custom : BookNamesStatus
}

internal fun bookNamesStatus(hasFileNames: Boolean, overrideCount: Int): BookNamesStatus = when {
    hasFileNames && overrideCount > 0 -> BookNamesStatus.Edited(overrideCount)
    hasFileNames -> BookNamesStatus.FromFile
    overrideCount >= BOOK_COUNT -> BookNamesStatus.Custom
    overrideCount > 0 -> BookNamesStatus.PartlySet(overrideCount)
    else -> BookNamesStatus.NotSet
}

/** What Save writes: names trimmed, with blank ones and ones that only repeat the file's left out. */
internal fun savedBookOverrides(draft: Map<Int, String>, fileNames: Map<Int, String>): Map<Int, String> =
    draft.mapValues { it.value.trim() }.filter { (id, name) -> name.isNotEmpty() && name != fileNames[id] }

/** Every book's name in English and in the app's language, by position in the canon. */
internal data class BookTitles(val english: List<String>, val localized: List<String>) {
    fun englishOf(id: Int): String = english.getOrNull(id - 1).orEmpty()
    fun localizedOf(id: Int): String = localized.getOrNull(id - 1).orEmpty()
}

/** The book ids to list, by testament or override and by any name a book goes by. */
internal fun bookIdsShown(
    filter: BookNameFilter,
    query: String,
    draft: Map<Int, String>,
    fileNames: Map<Int, String>,
    titles: BookTitles,
): List<Int> {
    val wanted = query.trim()
    return (1..BOOK_COUNT).filter { id ->
        val inFilter = when (filter) {
            BookNameFilter.ALL -> true
            BookNameFilter.OLD_TESTAMENT -> id <= OLD_TESTAMENT_BOOKS
            BookNameFilter.NEW_TESTAMENT -> id > OLD_TESTAMENT_BOOKS
            BookNameFilter.OVERRIDDEN -> id in draft
        }
        val names = listOfNotNull(fileNames[id], titles.englishOf(id), titles.localizedOf(id), draft[id])
        inFilter && (wanted.isEmpty() || names.any { it.contains(wanted, ignoreCase = true) })
    }
}

internal enum class BookNamesRead { ALL, SOME, NONE, FAILED }

internal data class ModuleBookNames(val names: Map<Int, String>, val read: BookNamesRead)

/** The names [fileName]'s own header gives its books, by book id; null until it has been read. */
@Composable
internal fun rememberModuleBookNames(storageDirectory: String, fileName: String): ModuleBookNames? {
    var names by remember(storageDirectory, fileName) { mutableStateOf<ModuleBookNames?>(null) }
    LaunchedEffect(storageDirectory, fileName) {
        names = withContext(Dispatchers.IO) { readModuleBookNames(storageDirectory, fileName) }
    }
    return names
}

internal fun readModuleBookNames(storageDirectory: String, fileName: String): ModuleBookNames {
    val file = File(storageDirectory, fileName)
    if (storageDirectory.isEmpty() || fileName.isEmpty() || !file.isFile) {
        return ModuleBookNames(emptyMap(), BookNamesRead.FAILED)
    }
    val bible = Bible().apply { loadBooksOnly(file.absolutePath) }
    val names = bible.getBooks().withIndex()
        .associate { (index, name) -> bible.getBookId(index) to name }
        .filterValues { !isPlaceholderBookName(it) }
    val failed = bible.loadError != null
    val read = when {
        names.isEmpty() -> if (failed) BookNamesRead.FAILED else BookNamesRead.NONE
        failed || names.keys.count { it in 1..BOOK_COUNT } < BOOK_COUNT -> BookNamesRead.SOME
        else -> BookNamesRead.ALL
    }
    return ModuleBookNames(names, read)
}
