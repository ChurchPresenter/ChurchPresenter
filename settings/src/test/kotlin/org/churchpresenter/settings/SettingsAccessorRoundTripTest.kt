package org.churchpresenter.settings

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsAccessorRoundTripTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun accessorsOf(value: Any): List<java.lang.reflect.Method> =
        value.javaClass.declaredMethods.filter {
            it.parameterCount == 0 &&
                !java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                java.lang.reflect.Modifier.isPublic(it.modifiers) &&
                (it.name.startsWith("get") || it.name.startsWith("is")) &&
                it.returnType != Void.TYPE
        }

    private fun <T : Any> assertEveryAccessorSurvives(serializer: KSerializer<T>, value: T, expectedAtLeast: Int) {
        val decoded = json.decodeFromString(serializer, json.encodeToString(serializer, value))
        val accessors = accessorsOf(value)
        assertTrue(
            accessors.size >= expectedAtLeast,
            "${value.javaClass.simpleName} exposes ${accessors.size} accessors, expected at least $expectedAtLeast",
        )
        accessors.forEach { accessor ->
            assertEquals(accessor.invoke(value), accessor.invoke(decoded), "${accessor.name} changed on reload")
        }
    }

    @Test
    fun `every bible setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(BibleSettings.serializer(), BibleSettings(), expectedAtLeast = 80)
    }

    @Test
    fun `every song setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(SongSettings.serializer(), SongSettings(), expectedAtLeast = 40)
    }

    @Test
    fun `every top-level setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(AppSettings.serializer(), AppSettings(), expectedAtLeast = 20)
    }

    @Test
    fun `every projection setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(ProjectionSettings.serializer(), ProjectionSettings(), expectedAtLeast = 5)
    }

    @Test
    fun `every background setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(BackgroundSettings.serializer(), BackgroundSettings(), expectedAtLeast = 3)
    }

    @Test
    fun `every announcements setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(AnnouncementsSettings.serializer(), AnnouncementsSettings(), expectedAtLeast = 10)
    }

    @Test
    fun `every server setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(ServerSettings.serializer(), ServerSettings(), expectedAtLeast = 5)
    }

    @Test
    fun `every stage monitor setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(StageMonitorSettings.serializer(), StageMonitorSettings(), expectedAtLeast = 3)
    }

    @Test
    fun `every picture setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(PictureSettings.serializer(), PictureSettings(), expectedAtLeast = 3)
    }

    @Test
    fun `every atem setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(AtemSettings.serializer(), AtemSettings(), expectedAtLeast = 5)
    }

    @Test
    fun `every dictionary setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(DictionarySettings.serializer(), DictionarySettings(), expectedAtLeast = 10)
    }

    @Test
    fun `every presentation setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(PresentationSettings.serializer(), PresentationSettings(), expectedAtLeast = 3)
    }

    @Test
    fun `every qa setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(QASettings.serializer(), QASettings(), expectedAtLeast = 5)
    }

    @Test
    fun `every stt setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(STTSettings.serializer(), STTSettings(), expectedAtLeast = 5)
    }

    @Test
    fun `every instance link setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(InstanceLinkSettings.serializer(), InstanceLinkSettings(), expectedAtLeast = 3)
    }

    @Test
    fun `every obs setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(OBSSettings.serializer(), OBSSettings(), expectedAtLeast = 3)
    }

    @Test
    fun `every streaming setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(StreamingSettings.serializer(), StreamingSettings(), expectedAtLeast = 3)
    }

    @Test
    fun `every window layout setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(WindowLayoutSettings.serializer(), WindowLayoutSettings(), expectedAtLeast = 4)
    }

    @Test
    fun `every bible engine setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(BibleEngineSettings.serializer(), BibleEngineSettings(), expectedAtLeast = 4)
    }

    @Test
    fun `every planning center setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(PlanningCenterSettings.serializer(), PlanningCenterSettings(), expectedAtLeast = 3)
    }

    @Test
    fun `every stock photo setting is readable and unchanged after a reload`() {
        assertEveryAccessorSurvives(StockPhotoSettings.serializer(), StockPhotoSettings(), expectedAtLeast = 2)
    }
}
