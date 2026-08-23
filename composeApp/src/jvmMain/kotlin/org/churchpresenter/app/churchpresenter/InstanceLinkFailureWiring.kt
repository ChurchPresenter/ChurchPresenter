package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.remote.instanceLinkPictureCacheDir
import org.churchpresenter.companionserver.InstanceLinkLogSide
import org.churchpresenter.companionserver.InstanceLinkLogger
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkCommandFailure
import org.churchpresenter.app.churchpresenter.viewmodel.InstanceLinkViewModel

/**
 * Collects the failures a follower reports back, so a command that silently did nothing on the
 * other machine surfaces here instead of being lost.
 *
 * Lifted out of `main()`: an ordinary effect with no window attached, so unlike the rest of
 * main.kt it can be composed — and tested — on its own.
 */
@Composable
internal fun InstanceLinkFailureWiring(
    instanceLinkViewModel: InstanceLinkViewModel,
    instanceLinkCommandFailures: MutableList<InstanceLinkCommandFailure>,
) {
    LaunchedEffect(instanceLinkViewModel) {
        instanceLinkViewModel.commandFailures.collect { failure ->
            instanceLinkCommandFailures.add(failure)
        }
    }
    // The primary's picture folders changed — cached picture files are keyed by folderId+index
    // only, so a replaced image at the same position would otherwise be served stale forever.
    // Clearing the whole cache is cheap: live pictures re-fetch lazily on next display.
    LaunchedEffect(instanceLinkViewModel) {
        instanceLinkViewModel.picturesUpdatedSignal.collect { signal ->
            if (!shouldInvalidatePictureCache(signal)) return@collect
            withContext(Dispatchers.IO) {
                instanceLinkPictureCacheDir.listFiles()?.forEach { it.delete() }
            }
            InstanceLinkLogger.log(
                InstanceLinkLogSide.FOLLOWER, "cache_invalidated",
                mapOf("kind" to "pictures", "trigger" to "pictures_updated")
            )
        }
    }
}
