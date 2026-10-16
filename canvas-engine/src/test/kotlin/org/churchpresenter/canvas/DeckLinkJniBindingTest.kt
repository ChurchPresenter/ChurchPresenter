package org.churchpresenter.canvas

import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager as DeckLinkJni

class DeckLinkJniBindingTest {

    private val nativeSource = File("../native/decklink/decklink_jni.cpp")

    private val exported: Set<String> by lazy {
        Regex("""Java_org_churchpresenter_app_churchpresenter_composables_DeckLinkManager_(\w+)""")
            .findAll(nativeSource.readText())
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun `the natives sit on the class the library was built against`() {
        assertEquals(
            "org.churchpresenter.app.churchpresenter.composables.DeckLinkManager",
            DeckLinkJni::class.java.name,
        )
    }

    @Test
    fun `every function the library exports has its native declaration, unmangled`() {
        assertTrue(exported.isNotEmpty(), "no DeckLink exports found in ${nativeSource.path}")
        val natives = DeckLinkJni::class.java.declaredMethods
            .filter { Modifier.isNative(it.modifiers) }
            .map { it.name }
            .toSet()

        assertEquals(exported, natives)
    }
}
