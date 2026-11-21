package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.allStringResources
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

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
 * Call it off the event thread, after the main window's first frame; failures are handled as
 * [readAhead] says. Strings in a language chosen later are read when they are first shown.
 *
 * @return how many strings were read.
 */
internal suspend fun warmStrings(
    resources: Collection<StringResource> = Res.allStringResources.values,
    read: suspend (StringResource) -> Unit = { getString(it) },
): Int = readAhead(resources, "strings", read)
