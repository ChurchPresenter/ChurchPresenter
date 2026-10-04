package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



/**
 * [readBibleFolderListing] for [directory], `null` until the scan lands.
 *
 * The scan runs on `Dispatchers.IO`, so the caller paints immediately and fills in — a caller that
 * shows a list must show a "scanning" state for the null, never an "empty folder" verdict.
 *
 * `remember(directory)` drops back to null when the folder changes, so one folder's modules are
 * never shown against another's.
 */
@Composable
fun rememberBibleFolderListing(directory: String): BibleFolderListing? {
    var listing by remember(directory) { mutableStateOf<BibleFolderListing?>(null) }
    LaunchedEffect(directory) {
        listing = withContext(Dispatchers.IO) { readBibleFolderListing(directory) }
    }
    return listing
}
