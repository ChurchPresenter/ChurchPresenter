package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Every settings type is persisted to and reloaded from JSON, so a field that doesn't survive the
 * trip silently loses an operator's configuration on the next launch. These round-trips encode with
 * `encodeDefaults = true` (writing every field, not just changed ones) and decode back, which
 * exercises each generated serializer end to end and pins that a value equals itself after the trip.
 */
class SettingsSerializationTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private inline fun <reified T : Any> assertRoundTrips(value: T) {
        val decoded = json.decodeFromString<T>(json.encodeToString(value))
        assertEquals(value, decoded, "${value::class.simpleName} did not survive a JSON round-trip")
        assertEquals(value.hashCode(), decoded.hashCode())
        assertTrue(value.toString().isNotEmpty())
    }

    /**
     * Changes one primitive field at a time (decoding a JSON object that sets only that field) and
     * asserts the result no longer equals the default — reaching every field's own branch in the
     * generated equals(), and proving a field can't silently be dropped from equality/persistence.
     */
    private inline fun <reified T : Any> assertEqualsAccountsForEveryPrimitiveField(default: T) {
        val serializer = serializer<T>()
        val descriptor = serializer.descriptor
        for (i in 0 until descriptor.elementsCount) {
            val candidates = when (descriptor.getElementDescriptor(i).kind) {
                PrimitiveKind.STRING -> listOf("\"__cp_alt_1__\"", "\"__cp_alt_2__\"")
                PrimitiveKind.BOOLEAN -> listOf("true", "false")
                PrimitiveKind.INT, PrimitiveKind.SHORT, PrimitiveKind.BYTE -> listOf("2147483111", "-2147483111")
                PrimitiveKind.LONG -> listOf("9000000000111", "-9000000000111")
                PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> listOf("1234567.75", "-1234567.75")
                else -> continue
            }
            val name = descriptor.getElementName(i)
            val differs = candidates.any { candidate ->
                runCatching { json.decodeFromString(serializer, "{\"$name\":$candidate}") != default }
                    .getOrDefault(false)
            }
            assertTrue(differs, "equals() must account for field '$name' of ${T::class.simpleName}")
        }
    }

    @Test
    fun `the top-level settings aggregate round-trips with all of its nested settings`() {
        assertRoundTrips(AppSettings())
    }

    @Test
    fun `network and integration settings round-trip`() {
        assertRoundTrips(ServerSettings())
        assertRoundTrips(InstanceLinkSettings())
        assertRoundTrips(AtemSettings())
        assertRoundTrips(OBSSettings())
        assertRoundTrips(StreamingSettings())
        assertRoundTrips(PlanningCenterSettings())
        assertRoundTrips(CompanionSatelliteSettings())
        assertRoundTrips(PresentationRemoteSettings())
        assertRoundTrips(BibleEngineSettings())
    }

    @Test
    fun `content and library settings round-trip`() {
        assertRoundTrips(SongSettings())
        assertRoundTrips(BibleSettings())
        assertRoundTrips(DictionarySettings())
        assertRoundTrips(PresentationSettings())
        assertRoundTrips(PictureSettings())
        assertRoundTrips(AnnouncementsSettings())
        assertRoundTrips(STTSettings())
        assertRoundTrips(QASettings())
    }

    @Test
    fun `output and appearance settings round-trip`() {
        assertRoundTrips(ProjectionSettings())
        assertRoundTrips(ScreenAssignment())
        assertRoundTrips(BackgroundSettings())
        assertRoundTrips(BackgroundConfig())
        assertRoundTrips(StageMonitorSettings())
        assertRoundTrips(StageMonitorZoneStyle())
        assertRoundTrips(WindowLayoutSettings())
        assertRoundTrips(StockPhotoSettings())
    }

    @Test
    fun `lower-third and bookmark settings round-trip`() {
        assertRoundTrips(LottiePreset())
        assertRoundTrips(LottieSearchReplacePair())
        assertRoundTrips(WebBookmark())
        assertRoundTrips(StoryPromptState())
    }

    @Test
    fun `copy produces a value that no longer equals the original`() {
        assertNotEquals(AnnouncementsSettings(), AnnouncementsSettings().copy(text = "changed"))
        assertNotEquals(AppSettings(), AppSettings().copy(settingsVersion = 999))
        assertNotEquals(AtemSettings(), AtemSettings().copy(host = "changed-host"))
        assertNotEquals(BackgroundConfig(), BackgroundConfig().copy(backgroundType = "changed-xyz"))
        assertNotEquals(BackgroundSettings(), BackgroundSettings().copy(defaultBackgroundColor = "#123456"))
        assertNotEquals(BibleEngineSettings(), BibleEngineSettings().copy(port = 1))
        assertNotEquals(BibleSettings(), BibleSettings().copy(storageDirectory = "changed"))
        assertNotEquals(CompanionSatelliteSettings(), CompanionSatelliteSettings().copy(name = "changed"))
        assertNotEquals(DictionarySettings(), DictionarySettings().copy(wordColor = "#123456"))
        assertNotEquals(InstanceLinkSettings(), InstanceLinkSettings().copy(enabled = true))
        assertNotEquals(LottiePreset(), LottiePreset().copy(id = "fixed-xyz"))
        assertNotEquals(LottieSearchReplacePair(), LottieSearchReplacePair().copy(search = "changed"))
        assertNotEquals(OBSSettings(), OBSSettings().copy(enabled = true))
        assertNotEquals(PictureSettings(), PictureSettings().copy(storageDirectory = "changed"))
        assertNotEquals(PlanningCenterSettings(), PlanningCenterSettings().copy(accessToken = "changed"))
        assertNotEquals(
            PresentationRemoteSettings(),
            PresentationRemoteSettings().copy(remoteControlEnabled = true),
        )
        assertNotEquals(PresentationSettings(), PresentationSettings().copy(animationType = "changed-xyz"))
        assertNotEquals(ProjectionSettings(), ProjectionSettings().copy(windowTop = 999))
        assertNotEquals(QASettings(), QASettings().copy(textColor = "#123456"))
        assertNotEquals(ScreenAssignment(), ScreenAssignment().copy(targetDisplay = 99))
        assertNotEquals(ServerSettings(), ServerSettings().copy(enabled = true))
        assertNotEquals(SongSettings(), SongSettings().copy(storageDirectory = "changed"))
        assertNotEquals(
            StageMonitorSettings(),
            StageMonitorSettings().copy(metronomePosition = MetronomePosition.CENTER),
        )
        assertNotEquals(StageMonitorZoneStyle(), StageMonitorZoneStyle().copy(fontType = "Courier"))
        assertNotEquals(StockPhotoSettings(), StockPhotoSettings().copy(pexelsApiKey = "changed"))
        assertNotEquals(StreamingSettings(), StreamingSettings().copy(lowerThirdFolder = "changed"))
        assertNotEquals(STTSettings(), STTSettings().copy(serverUrl = "changed"))
        assertNotEquals(WebBookmark(), WebBookmark().copy(url = "changed"))
        assertNotEquals(WindowLayoutSettings(), WindowLayoutSettings().copy(schedulePanelWidthDp = 99999))
    }

    private inline fun <reified T : Any> assertDecodesFromEmptyObject() {
        val decoded = json.decodeFromString<T>("{}")
        assertTrue(decoded.toString().isNotEmpty(), "an empty JSON object must decode to all defaults")
    }

    @Test
    fun `every settings type decodes from an empty object using its defaults`() {
        assertDecodesFromEmptyObject<AppSettings>()
        assertDecodesFromEmptyObject<ServerSettings>()
        assertDecodesFromEmptyObject<InstanceLinkSettings>()
        assertDecodesFromEmptyObject<AtemSettings>()
        assertDecodesFromEmptyObject<OBSSettings>()
        assertDecodesFromEmptyObject<StreamingSettings>()
        assertDecodesFromEmptyObject<PlanningCenterSettings>()
        assertDecodesFromEmptyObject<CompanionSatelliteSettings>()
        assertDecodesFromEmptyObject<PresentationRemoteSettings>()
        assertDecodesFromEmptyObject<BibleEngineSettings>()
        assertDecodesFromEmptyObject<SongSettings>()
        assertDecodesFromEmptyObject<BibleSettings>()
        assertDecodesFromEmptyObject<DictionarySettings>()
        assertDecodesFromEmptyObject<PresentationSettings>()
        assertDecodesFromEmptyObject<PictureSettings>()
        assertDecodesFromEmptyObject<AnnouncementsSettings>()
        assertDecodesFromEmptyObject<STTSettings>()
        assertDecodesFromEmptyObject<QASettings>()
        assertDecodesFromEmptyObject<ProjectionSettings>()
        assertDecodesFromEmptyObject<ScreenAssignment>()
        assertDecodesFromEmptyObject<BackgroundSettings>()
        assertDecodesFromEmptyObject<BackgroundConfig>()
        assertDecodesFromEmptyObject<StageMonitorSettings>()
        assertDecodesFromEmptyObject<StageMonitorZoneStyle>()
        assertDecodesFromEmptyObject<WindowLayoutSettings>()
        assertDecodesFromEmptyObject<StockPhotoSettings>()
        assertDecodesFromEmptyObject<LottiePreset>()
        assertDecodesFromEmptyObject<LottieSearchReplacePair>()
        assertDecodesFromEmptyObject<WebBookmark>()
        assertDecodesFromEmptyObject<StoryPromptState>()
    }

    @Test
    fun `every settings enum resolves each entry back from its name`() {
        fun <T : Enum<T>> checkAllEntries(entries: List<T>, valueOf: (String) -> T) {
            assertTrue(entries.isNotEmpty())
            for (entry in entries) assertEquals(entry, valueOf(entry.name))
        }
        checkAllEntries(BibleSyncMode.entries, BibleSyncMode::valueOf)
        checkAllEntries(InstanceLinkRole.entries, InstanceLinkRole::valueOf)
        checkAllEntries(StageMonitorContentType.entries, StageMonitorContentType::valueOf)
        checkAllEntries(StageMonitorZone.entries, StageMonitorZone::valueOf)
        checkAllEntries(StageMonitorStyleZone.entries, StageMonitorStyleZone::valueOf)
        checkAllEntries(MetronomePosition.entries, MetronomePosition::valueOf)
    }

    @Test
    fun `every settings enum entry round-trips through json`() {
        for (v in BibleSyncMode.entries) assertEquals(v, json.decodeFromString<BibleSyncMode>(json.encodeToString(v)))
        for (v in InstanceLinkRole.entries) assertEquals(
            v,
            json.decodeFromString<InstanceLinkRole>(json.encodeToString(v)),
        )
        for (v in StageMonitorContentType.entries) assertEquals(
            v,
            json.decodeFromString<StageMonitorContentType>(json.encodeToString(v)),
        )
        for (v in StageMonitorZone.entries) assertEquals(
            v,
            json.decodeFromString<StageMonitorZone>(json.encodeToString(v)),
        )
        for (v in StageMonitorStyleZone.entries) assertEquals(
            v,
            json.decodeFromString<StageMonitorStyleZone>(json.encodeToString(v)),
        )
        for (v in MetronomePosition.entries) assertEquals(
            v,
            json.decodeFromString<MetronomePosition>(json.encodeToString(v)),
        )
    }

    /** Invokes component1()..componentN() so the data class's generated destructuring accessors are
     *  executed (covered) without hand-writing 100+-element destructuring for the large classes. */
    private fun exerciseComponents(instance: Any): Int {
        var i = 1
        while (true) {
            val method = try {
                instance::class.java.getMethod("component$i")
            } catch (_: NoSuchMethodException) {
                break
            }
            method.invoke(instance)
            i++
        }
        return i - 1
    }

    @Test
    fun `every settings type exposes a component accessor for each field`() {
        val all = listOf<Any>(
            AnnouncementsSettings(), AppSettings(), AtemSettings(), BackgroundConfig(), BackgroundSettings(),
            BibleEngineSettings(), BibleSettings(), CompanionSatelliteSettings(), DictionarySettings(),
            InstanceLinkSettings(), LottiePreset(), LottieSearchReplacePair(), OBSSettings(), PictureSettings(),
            PlanningCenterSettings(), PresentationRemoteSettings(), PresentationSettings(), ProjectionSettings(),
            QASettings(), ScreenAssignment(), ServerSettings(), SongSettings(), StageMonitorSettings(),
            StageMonitorZoneStyle(), StockPhotoSettings(), StreamingSettings(), STTSettings(), WebBookmark(),
            WindowLayoutSettings(), StoryPromptState(),
        )
        for (settings in all) {
            assertTrue(exerciseComponents(settings) > 0, "${settings::class.simpleName} has no component accessors")
        }
    }

    @Test
    fun `a populated aggregate keeps every field it was given`() {
        val populated = AppSettings(
            serverSettings = ServerSettings(enabled = true, port = 9000, apiKeyEnabled = true, apiKey = "k"),
            analyticsReportingEnabled = false,
            lastUpdateCheckTimestamp = 1_726_000_000_000L,
        )
        val decoded = json.decodeFromString<AppSettings>(json.encodeToString(populated))
        assertEquals(populated, decoded)
        assertEquals(9000, decoded.serverSettings.port)
        assertEquals(false, decoded.analyticsReportingEnabled)
        assertEquals(1_726_000_000_000L, decoded.lastUpdateCheckTimestamp)
    }

    @Test
    fun `the story prompt schedule survives a round-trip`() {
        val populated = AppSettings(
            storyPrompt = StoryPromptState(
                installedAtMillis = 1_726_000_000_000L,
                activeWeeks = setOf(2854L, 2855L, 2856L, 2857L),
                timesShown = 2,
                lastShownAtMillis = 1_728_000_000_000L,
                finished = true,
            ),
        )
        val decoded = json.decodeFromString<AppSettings>(json.encodeToString(populated))

        assertEquals(populated.storyPrompt, decoded.storyPrompt)
        assertEquals(setOf(2854L, 2855L, 2856L, 2857L), decoded.storyPrompt.activeWeeks)
        assertEquals(2, decoded.storyPrompt.timesShown)
        assertTrue(decoded.storyPrompt.finished)
    }

    @Test
    fun `settings written before the story prompt existed decode to a fresh schedule`() {
        val decoded = json.decodeFromString<AppSettings>("""{"language":"en"}""")

        assertEquals(StoryPromptState(), decoded.storyPrompt)
        assertEquals(0L, decoded.storyPrompt.installedAtMillis)
    }

    @Test
    fun `equals distinguishes every primitive field of the network and integration settings`() {
        assertEqualsAccountsForEveryPrimitiveField(ServerSettings())
        assertEqualsAccountsForEveryPrimitiveField(InstanceLinkSettings())
        assertEqualsAccountsForEveryPrimitiveField(AtemSettings())
        assertEqualsAccountsForEveryPrimitiveField(OBSSettings())
        assertEqualsAccountsForEveryPrimitiveField(StreamingSettings())
        assertEqualsAccountsForEveryPrimitiveField(PlanningCenterSettings())
        assertEqualsAccountsForEveryPrimitiveField(PresentationRemoteSettings())
        assertEqualsAccountsForEveryPrimitiveField(BibleEngineSettings())
    }

    @Test
    fun `equals distinguishes every primitive field of the content and library settings`() {
        assertEqualsAccountsForEveryPrimitiveField(SongSettings())
        assertEqualsAccountsForEveryPrimitiveField(BibleSettings())
        assertEqualsAccountsForEveryPrimitiveField(DictionarySettings())
        assertEqualsAccountsForEveryPrimitiveField(PresentationSettings())
        assertEqualsAccountsForEveryPrimitiveField(PictureSettings())
        assertEqualsAccountsForEveryPrimitiveField(AnnouncementsSettings())
        assertEqualsAccountsForEveryPrimitiveField(STTSettings())
        assertEqualsAccountsForEveryPrimitiveField(QASettings())
    }

    @Test
    fun `equals distinguishes every primitive field of the output and appearance settings`() {
        assertEqualsAccountsForEveryPrimitiveField(ScreenAssignment())
        assertEqualsAccountsForEveryPrimitiveField(BackgroundSettings())
        assertEqualsAccountsForEveryPrimitiveField(BackgroundConfig())
        assertEqualsAccountsForEveryPrimitiveField(StageMonitorZoneStyle())
        assertEqualsAccountsForEveryPrimitiveField(WindowLayoutSettings())
        assertEqualsAccountsForEveryPrimitiveField(StockPhotoSettings())
        assertEqualsAccountsForEveryPrimitiveField(LottieSearchReplacePair())
        assertEqualsAccountsForEveryPrimitiveField(WebBookmark())
        assertEqualsAccountsForEveryPrimitiveField(AppSettings())
    }
}
