package org.churchpresenter.settings

/*
 * A Bible translation's look stays with its place in the stack ([BibleSettings.looksFollowPosition]).
 *
 * A profile stores each look under the translation's file name -- its entry in
 * `bibleSettings.translations`, its boxes in `bibleSettings.textBoxes`, and a linked profile's own
 * values among its `overrides` -- and resolution matches them back on by name. So rather than
 * resolve by position, a stack edit renames them: the look that was KJV's at place 1 becomes the
 * look of whatever is at place 1 now. Everything that reads a look by name is left as it was.
 */

/**
 * Puts the Bible [fileName] in place [index], instead of the one there. Its look is the one that
 * place had when looks follow the place ([BibleSettings.looksFollowPosition]); otherwise its own,
 * or the All look when it has none yet. Every selection stays on the place: the stack's positions
 * do not change. [fileName] already in the stack, or blank, changes nothing.
 */
fun AppSettings.replaceBibleTranslation(index: Int, fileName: String): AppSettings {
    val stack = bibleSettings.translationList()
    if (index !in stack.indices || fileName.isBlank() || stack.any { it.fileName == fileName }) return this
    val replaced = bibleSettings.updateTranslation(index) {
        it.copy(fileName = fileName, bookNameOverrides = emptyMap())
    }
    return copy(bibleSettings = replaced).withLooksKeptInPlace(stack)
}

/** The renames that keep every place's look where it was, going from [before] to [after]. */
internal fun positionRenames(before: List<String>, after: List<String>): Map<String, String> =
    before.zip(after).filter { (old, new) -> old != new }.toMap()

/**
 * [this] with each look stored under a key of [renames] moved to its value, all at once -- so a
 * swap is a swap -- and any look already stored under a value that is not itself moving dropped,
 * since the look moving in replaces it.
 */
internal fun OutputProfile.withBibleLooksRenamed(renames: Map<String, String>): OutputProfile {
    if (renames.isEmpty()) return this
    val replaced = renames.values.toSet() - renames.keys
    val bs = bibleSettings
    val translations = bs.translations
        .filter { it.fileName !in replaced }
        .map { t -> renames[t.fileName]?.let { t.copy(fileName = it) } ?: t }
    val boxRenames = bs.textBoxes.keys.associateWith { key -> boxKeyRenamed(key, renames, replaced) }
    val boxes = bs.textBoxes.entries
        .mapNotNull { (key, box) -> boxRenames.getValue(key)?.let { it to box } }
        .toMap()
    val prefixes = translationPrefixes(renames, replaced) + boxRenames.mapNotNull { (key, renamed) ->
        if (renamed == key) null else member(BOXES_PATH, key) to renamed?.let { member(BOXES_PATH, it) }
    }
    return copy(
        bibleSettings = bs.copy(translations = translations, textBoxes = boxes),
        overrides = overrides.mapNotNull { path -> path.withPrefixRenamed(prefixes) }.toSet(),
    )
}

private const val TRANSLATIONS_PATH = "bibleSettings.translations"
private const val BOXES_PATH = "bibleSettings.textBoxes"
private const val LOWER_THIRD_SUFFIX = "@LT"

/** Each translation's path prefix, old to new -- or to null where its look is replaced. */
private fun translationPrefixes(renames: Map<String, String>, replaced: Set<String>): Map<String, String?> =
    renames.map { (old, new) -> named(TRANSLATIONS_PATH, old) to named(TRANSLATIONS_PATH, new) }.toMap() +
        replaced.associate { named(TRANSLATIONS_PATH, it) to null }

/**
 * A [textBoxKey] -- `ITEM#file` or `ITEM#file@LT` -- with its translation renamed, null where its
 * translation's look is replaced, unchanged where it names no translation that moves.
 */
private fun boxKeyRenamed(key: String, renames: Map<String, String>, replaced: Set<String>): String? {
    val hash = key.indexOf('#')
    if (hash < 0) return key
    val rest = key.substring(hash + 1)
    val lowerThird = rest.endsWith(LOWER_THIRD_SUFFIX)
    val file = if (lowerThird) rest.dropLast(LOWER_THIRD_SUFFIX.length) else rest
    return when (file) {
        in replaced -> null
        in renames -> textBoxKey(key.substring(0, hash), lowerThird, renames.getValue(file))
        else -> key
    }
}

/** [this] path under the first of [prefixes] it starts with, renamed or dropped (null). */
private fun String.withPrefixRenamed(prefixes: Map<String, String?>): String? {
    for ((old, new) in prefixes) {
        if (this == old || startsWith("$old.") || startsWith("$old[")) {
            return new?.let { it + substring(old.length) }
        }
    }
    return this
}

/**
 * The settings after a stack edit that took the document's stack from [before] to what it is now,
 * with the looks of every profile whose looks follow the place
 * ([BibleSettings.looksFollowPosition], the profile's own) carried to the places they belong to.
 * A profile whose looks follow the Bible keeps them under each Bible's name.
 */
internal fun AppSettings.withLooksKeptInPlace(before: List<BibleTranslationSettings>): AppSettings {
    val renames = positionRenames(before.map { it.fileName }, bibleSettings.translationList().map { it.fileName })
    if (renames.isEmpty()) return this
    val renamed = projectionSettings.copy(
        outputProfiles = projectionSettings.outputProfiles.map { profile ->
            if (profile.bibleSettings.looksFollowPosition) profile.withBibleLooksRenamed(renames) else profile
        },
    )
    // A follower whose toggle is its own renamed only its own values; what it inherits is written
    // again from its master, as after any other edit.
    val linked = renamed.outputProfiles.any { it.parentId != null }
    return copy(projectionSettings = if (linked) renamed.withLinksResolved() else renamed)
}
