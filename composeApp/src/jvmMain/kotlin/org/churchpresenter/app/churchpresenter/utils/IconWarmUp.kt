package org.churchpresenter.app.churchpresenter.utils

import kotlinx.coroutines.CancellationException
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.allDrawableResources
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.MissingResourceException
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import java.io.IOException

/**
 * Reads every one of the app's icons from the jar once, so that the first `painterResource` of each
 * does not wait on the disk on the event thread.
 *
 * Compose Resources loads an icon the first time it is drawn with `runBlocking` inside the
 * composition: it reads the file out of the jar and parses it. Its decoded-icon cache is private, so
 * this cannot fill it; what it does is read each file ahead, which brings those parts of the jar
 * into the system's file cache. On a slow disk that read is most of the wait; what is left on the
 * event thread is parsing a few kilobytes of vector XML.
 *
 * Call it off the event thread, after the main window's first frame.
 *
 * @return how many icons were read.
 */
internal suspend fun warmIcons(
    resources: Collection<DrawableResource> = Res.allDrawableResources.values,
    read: suspend (DrawableResource) -> Unit = { getDrawableResourceBytes(getSystemResourceEnvironment(), it) },
): Int = readAhead(resources, "icons", read)

/**
 * Runs [read] on each of [resources] and returns how many succeeded. One that fails to read is
 * counted and the rest go on; the count is logged once under [what], never thrown -- the screen will
 * simply read that one itself. A cancellation stops the run: the window is closing.
 */
internal suspend fun <T> readAhead(resources: Collection<T>, what: String, read: suspend (T) -> Unit): Int {
    var failed = 0
    var firstFailure: Exception? = null
    for (resource in resources) {
        try {
            read(resource)
        } catch (e: CancellationException) {
            // An IllegalStateException too, and never a failure to read.
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
        Log.warn(TAG, "$failed of ${resources.size} $what could not be read ahead: ${firstFailure::class.simpleName}")
    }
    return resources.size - failed
}

private const val TAG = "ResourceWarmUp"
