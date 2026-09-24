package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where the calendar lives when no folder has been chosen — the app data folder.
 *
 * Hoisted because it is a property of the machine, and the Storage card *prints it*. Under test
 * `AppDataDir` resolves inside the working copy's `build/` directory, so the committed images
 * carried the absolute path of the checkout that recorded them — `…/ChurchPresenter-tab-margin/
 * composeApp/build/tes…` — and every other checkout, worktree and CI runner drew its own path
 * instead. Twelve images failed for that and nothing else (issue #617).
 *
 * A composition local rather than a parameter: the row is four layers down from the settings dialog,
 * and threading a parameter through those four re-keys their `LongMethod` baseline entries, which
 * costs an extraction apiece for a value no one but a test ever sets. The same remedy the font
 * picker got — see `LocalFontPreviewFace`.
 *
 * This is the *fallback* only. A folder the operator has actually chosen is theirs and is drawn as
 * it stands, so `isDefault` still reads the stored setting rather than this.
 *
 * **Null means "resolve it the way the app always did"**, and the app never provides it. Defaulting
 * it to `AppDataDir.resolve()` here instead looked tidier and was not: a static local's default is
 * computed on first read, which happens before a suite has finished pointing `user.home` at its own
 * per-fork directory, so the row resolved against the real home, the folder was not there, and the
 * Storage card's "needs attention" count came out one higher. Keeping the fallback at the call site
 * leaves production behaviour byte for byte what it was, and only a test that *provides* a value
 * changes anything.
 */
internal val LocalDefaultCalendarFolder = staticCompositionLocalOf<String?> { null }
