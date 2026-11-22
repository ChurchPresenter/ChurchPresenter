package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.utils.AppDataDir
import org.churchpresenter.web.presenter.CefManager
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebEngineUseHookTest {

    @Test
    fun `the engine's first client marks the data folder`() {
        val previous = CefManager.onClientCreated
        val marker = File(AppDataDir.resolve(), "web-engine-used")
        val existed = marker.exists()
        try {
            WebEngineUse.markOnFirstClient()
            val hook = assertNotNull(CefManager.onClientCreated)

            hook()

            val deadline = System.nanoTime() + 5_000_000_000L
            while (!WebEngineUse.wasUsed(AppDataDir.resolve()) && System.nanoTime() < deadline) Thread.onSpinWait()
            assertTrue(WebEngineUse.wasUsed(AppDataDir.resolve()))
        } finally {
            CefManager.onClientCreated = previous
            if (!existed) marker.delete()
        }
    }
}
