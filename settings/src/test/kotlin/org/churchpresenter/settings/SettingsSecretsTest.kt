package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The passwords, sign-ins and API keys an export for sharing leaves out, and which ones an import
 * keeps: a shared file must carry none of them, and an import must never quietly swap this
 * computer's for a file's, or lose them to a file that has none.
 */
class SettingsSecretsTest {

    private val json = Json { encodeDefaults = true }

    /** Settings with every secret set, and a visible setting beside them to show nothing else moves. */
    private fun withEverySecret(tag: String) = AppSettings(
        planningCenterSettings = PlanningCenterSettings(
            accessToken = "$tag-access",
            refreshToken = "$tag-refresh",
            tokenExpiresAtEpochMs = 42L,
            connectedPersonName = "$tag person",
        ),
        calendarSync = CalendarSyncSettings(
            instanceId = "$tag-id",
            desktopToken = "$tag-token",
            instanceKey = "$tag-key",
        ),
        obsSettings = OBSSettings(password = "$tag-obs", host = "obs.local"),
        serverSettings = ServerSettings(apiKey = "$tag-server"),
        instanceLink = InstanceLinkSettings(apiKey = "$tag-link"),
        stockPhotoSettings = StockPhotoSettings(pexelsApiKey = "$tag-pexels", pixabayApiKey = "$tag-pixabay"),
    )

    @Test
    fun `an export for sharing carries no password, sign-in or key, and keeps everything else`() {
        val shared = withEverySecret("mine").withoutSecrets()
        assertFalse(shared.hasSecrets)
        assertEquals(PlanningCenterSettings(), shared.planningCenterSettings)
        assertEquals(CalendarSyncSettings(), shared.calendarSync)
        assertEquals("", shared.obsSettings.password)
        assertEquals("obs.local", shared.obsSettings.host, "only the secret goes")
        assertEquals("", shared.serverSettings.apiKey)
        assertEquals("", shared.instanceLink.apiKey)
        assertEquals(StockPhotoSettings(), shared.stockPhotoSettings)
    }

    /**
     * A guard for settings added later: every field in the shared file whose name says it is a
     * credential must come out empty. A new password or key that is not added to `withoutSecrets`
     * fails here instead of leaking into every file someone attaches to an issue.
     */
    @Test
    fun `no credential-named field survives an export for sharing`() {
        val credentialName = Regex("(?i)(token|password|secret|apikey|clientkey|instancekey|credential)")
        val leaks = mutableListOf<String>()
        fun walk(element: JsonElement, path: String) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) ->
                    val here = "$path.$key"
                    val filled = value is JsonPrimitive && value.isString && value.content.isNotBlank()
                    if (filled && credentialName.containsMatchIn(key)) leaks += here
                    walk(value, here)
                }
                is JsonArray -> element.forEachIndexed { i, child -> walk(child, "$path[$i]") }
                else -> Unit
            }
        }
        walk(json.encodeToJsonElement(withEverySecret("mine").withoutSecrets()), "")
        assertEquals(emptyList(), leaks)
    }

    @Test
    fun `settings know whether they carry a secret, calendar pairing aside`() {
        assertFalse(AppSettings().hasSecrets)
        assertTrue(withEverySecret("x").hasSecrets)
        assertTrue(AppSettings(obsSettings = OBSSettings(password = "p")).hasSecrets)
        assertFalse(
            AppSettings(calendarSync = CalendarSyncSettings(desktopToken = "t")).hasSecrets,
            "calendar sync is never exported and always kept, so it does not ask",
        )
    }

    @Test
    fun `an import keeps this computer's secrets unless told to use the file's`() {
        val mine = withEverySecret("mine")
        val file = withEverySecret("file").copy(obsSettings = OBSSettings(password = "file-obs", host = "file.local"))

        val kept = importedSettings(file, mine, useFileSecrets = false)
        assertEquals("mine-access", kept.planningCenterSettings.accessToken)
        assertEquals("mine-obs", kept.obsSettings.password)
        assertEquals("file.local", kept.obsSettings.host, "the file's own settings still come in")
        assertEquals("mine-pexels", kept.stockPhotoSettings.pexelsApiKey)

        val taken = importedSettings(file, mine, useFileSecrets = true)
        assertEquals("file-access", taken.planningCenterSettings.accessToken)
        assertEquals("file-obs", taken.obsSettings.password)
        assertEquals("file-server", taken.serverSettings.apiKey)
        assertEquals(mine.calendarSync, taken.calendarSync, "calendar sync is always this computer's")
    }

    @Test
    fun `a file shared without secrets never wipes this computer's`() {
        val mine = withEverySecret("mine")
        val shared = withEverySecret("file").withoutSecrets()
        val imported = importedSettings(shared, mine, useFileSecrets = true)
        assertEquals("mine-access", imported.planningCenterSettings.accessToken)
        assertEquals("mine-link", imported.instanceLink.apiKey)
        assertEquals(mine.calendarSync, imported.calendarSync)
    }
}
