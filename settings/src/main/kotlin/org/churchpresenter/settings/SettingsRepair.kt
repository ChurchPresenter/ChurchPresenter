package org.churchpresenter.settings

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// Repairs applied to every decoded document, and the version a raw document says it is.

/**
 * Puts the translation stack back in step with the legacy bible pair it mirrors.
 *
 * An invariant, not a migration, which is why it runs on every load and not behind a version
 * gate. `primaryBible`/`secondaryBible` are only kept so an older build can still read the file;
 * anything that sets one of them without going through [BibleSettings.withTranslations] leaves a
 * current-version document with a configured pair and an empty stack. That document is never
 * migrated — it is already at the current version — so before this it stayed broken for good.
 * Nothing shows it either: [BibleSettings.translationList] falls back to the pair, so the app
 * presents correctly right up until the first stack edit rewrites the pair from a list that
 * never held those bibles, and the operator's translations disappear.
 *
 * Safe on a stack that is empty on purpose: emptying it through `withTranslations` clears the
 * legacy pair too, so there is nothing to put back. Idempotent, by
 * [BibleSettings.migrateTranslations]'s own guard.
 */
internal fun AppSettings.repaired(): AppSettings =
    copy(
        bibleSettings = bibleSettings.migrateTranslations().migrateAllLayer(),
        songSettings = songSettings.migrateSongNumberStyle().migrateElementPositions(),
        projectionSettings = projectionSettings.copy(
            outputProfiles = projectionSettings.outputProfiles.map(::repairedProfile),
        ).withLinksResolved().withProfileReferencesRepaired(),
    )

/**
 * The invariants [repaired] keeps on the document, kept on one profile's copy too -- an output
 * draws from its profile, so a title position the document has had fixed but a profile has not
 * is a title missing from that screen.
 *
 * Not [migrateSongNumberStyle]: that is a one-time carry of the title's look onto a number that
 * predates having its own, and [migrateRepairedSongAndBible] has already done it to the document
 * every profile was copied from. Run again here it would read a profile's own title styling as
 * that same old state and restyle a number the operator left plain.
 */
private fun repairedProfile(profile: OutputProfile): OutputProfile =
    profile.copy(
        bibleSettings = profile.bibleSettings.migrateTranslations().migrateAllLayer(),
        songSettings = profile.songSettings.migrateElementPositions(),
    )

/**
 * Schema version 12, first half. Writes [repaired]'s song and Bible fixes into the stored
 * document, so the profiles [migrateOutputProfiles] copies from it -- and the title-slide number
 * version 16 seeds from its song-number fields -- start from what the outputs actually drew.
 *
 * [repaired] only ever ran on the decoded document; before profiles that was enough, because
 * every output drew from the document. Copied raw, a profile would keep a title position the
 * presenter cannot place (so the title vanishes) and a song number that no longer inherits the
 * title's look.
 *
 * Only the keys the repair actually changes are written back, so the legacy keys later steps
 * still read are left exactly where they are.
 */
internal fun migrateRepairedSongAndBible(raw: String): String {
    val root = parseSettingsRoot(raw) ?: return raw
    val song = root["songSettings"] as? JsonObject
    val bible = root["bibleSettings"] as? JsonObject
    var updated = root
    if (song != null) {
        updated = JsonObject(
            updated + ("songSettings" to repairedSection(song, SongSettings.serializer()) {
                it.migrateSongNumberStyle().migrateElementPositions()
            }),
        )
    }
    if (bible != null) {
        updated = JsonObject(
            updated + ("bibleSettings" to repairedSection(bible, BibleSettings.serializer()) {
                it.migrateTranslations()
            }),
        )
    }
    return updated.toString()
}

/** [section] with only the fields [repair] changes rewritten, every other key left as stored. */
private fun <T> repairedSection(section: JsonObject, serializer: KSerializer<T>, repair: (T) -> T): JsonObject {
    val decoded = settingsJson.decodeFromJsonElement(serializer, section)
    val before = settingsJson.encodeToJsonElement(serializer, decoded).jsonObject
    val after = settingsJson.encodeToJsonElement(serializer, repair(decoded)).jsonObject
    val changed = after.filter { (key, value) -> before[key] != value }
    return if (changed.isEmpty()) section else JsonObject(section + changed)
}

/** Reads the document's schema version without decoding it; absent or unparseable means 0
 * (pre-versioning), which runs the full migration chain — the pre-versioning behaviour. */
internal fun readSettingsVersion(raw: String): Int =
    try {
        (settingsJson.parseToJsonElement(raw).jsonObject["settingsVersion"] as? JsonPrimitive)
            ?.content?.toIntOrNull() ?: 0
    } catch (_: Exception) {
        0
    }
