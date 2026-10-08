package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What auto start does on a Store install. `StorePackage` itself is tested in `:updater`. */
class AutoStartStoreInstallTest {

    @Test
    fun `auto start keeps the launcher path except for a store install`() {
        assertEquals("C:\\x.exe", AutoStartManager.launcherPath("C:\\x.exe", storeInstall = false))
        assertNull(AutoStartManager.launcherPath("C:\\x.exe", storeInstall = true))
        assertNull(AutoStartManager.launcherPath(null, storeInstall = false))
    }
}
