package org.churchpresenter.helper.pack

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The committed `wick-pack/pack.json` says what `source.json` says, and loads in this build without losing
 * anything. Fails after an edit to the source until `./gradlew :helper:buildWickPack` has been run.
 */
class WickPackSyncTest {

    private val source = WickPackSource.read()
    private val built = Json.decodeFromString(PackFile.serializer(), WickPackSource.packFile.readText())

    @Test
    fun `the committed pack is built from the committed source`() {
        val message = "wick-pack/pack.json is out of date: run ./gradlew :helper:buildWickPack"
        assertEquals(source.version, built.version, message)
        assertEquals(source.minApp, built.minApp, message)
        assertEquals(source.tours, built.tours, message)
        assertEquals(source.tips, built.tips, message)
        assertEquals(source.wanted(), built.phrases.map { it.split('\t').let { (t, text) -> t to text } }, message)
    }

    @Test
    fun `the committed pack loads whole in this build`() {
        val pack = assertNotNull(parseWickPack(WickPackSource.packFile.readText(), source.minApp))
        assertEquals(built.phrases.size, pack.phrases.size)
        assertEquals(built.tours.map { it.id }.toSet(), pack.tours.keys)
        assertEquals(built.tips.size, pack.tips.size)
    }
}
