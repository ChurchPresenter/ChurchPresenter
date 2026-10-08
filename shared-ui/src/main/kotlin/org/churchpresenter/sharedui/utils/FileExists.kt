package org.churchpresenter.sharedui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether a file is there, asked off the UI thread and remembered.
 *
 * A path on a share or a removable drive can take seconds to answer, and an output window composes
 * on the same thread as the operator's UI, so no composition may ask the disk. [known] is what the
 * last check found, which lets a path seen before draw right away while it is checked again.
 */
object FileExists {
    private val seen = ConcurrentHashMap<String, Boolean>()

    /** What the last [check] of [path] found, or null when it has never been checked. */
    fun known(path: String): Boolean? = seen[path]

    /** Asks the disk about [path] -- call this off the UI thread -- and remembers the answer. */
    fun check(path: String): Boolean = File(path).exists().also { seen[path] = it }
}

/**
 * Whether the file at [path] exists: null until it has been checked, false for a blank path.
 * A path checked before answers at once and is checked again in the background.
 */
@Composable
fun rememberFileExists(path: String?): Boolean? {
    if (path.isNullOrBlank()) return false
    var exists by remember(path) { mutableStateOf(FileExists.known(path)) }
    LaunchedEffect(path) { exists = withContext(Dispatchers.IO) { FileExists.check(path) } }
    return exists
}
