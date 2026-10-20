package org.churchpresenter.app.churchpresenter.utils

import kotlinx.coroutines.CancellationException
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.allStringResources
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.IOException

/**
 * Reads every one of the app's strings once, so that no screen pays for its first `stringResource`
 * on the event thread.
 *
 * Compose Resources reads a string the first time it is asked for with `runBlocking` inside the
 * composition: it opens the language's `strings.main.cvr` in the jar and inflates it up to that
 * string's offset. A screen opened for the first time does that for every string it shows, and on
 * a slow machine that is most of a second with the window frozen. What it reads lands in one
 * process-wide cache keyed by file, offset and size, which `getString` fills the same way, so once
 * this has run `stringResource` finds every string already there.
 *
 * Call it off the event thread, after the main window's first frame. A string that fails to load is
 * counted and the rest go on; the count is logged once, never thrown -- the screen will simply read
 * that string itself, as it would have without this. Strings in a language chosen later are read
 * when they are first shown.
 *
 * @return how many strings were read.
 */
internal suspend fun warmStrings(
    resources: Collection<StringResource> = Res.allStringResources.values,
    read: suspend (StringResource) -> Unit = { getString(it) },
): Int {
    var failed = 0
    var firstFailure: Exception? = null
    for (resource in resources) {
        try {
            read(resource)
        } catch (e: CancellationException) {
            // An IllegalStateException too, and never a failure to read: the window is closing.
            throw e
        } catch (e: MissingResourceException) {
            failed++
            firstFailure = firstFailure ?: e
        } catch (e: IOException) {
            failed++
            firstFailure = firstFailure ?: e
        } catch (e: IllegalStateException) {
            failed++
            firstFailure = firstFailure ?: e
        }
    }
    if (firstFailure != null) {
        Log.warn(TAG, "$failed of ${resources.size} strings could not be read ahead: ${firstFailure::class.simpleName}")
    }
    return resources.size - failed
}

private const val TAG = "StringWarmUp"
