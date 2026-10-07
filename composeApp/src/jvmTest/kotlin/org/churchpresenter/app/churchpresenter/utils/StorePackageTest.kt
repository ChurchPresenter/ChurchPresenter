package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorePackageTest {

    @Test
    fun `a launcher under WindowsApps is a store install`() {
        assertTrue(StorePackage.isUnderWindowsApps("C:\\Program Files\\WindowsApps\\Pkg_1.0\\app\\ChurchPresenter.exe"))
        assertTrue(StorePackage.isUnderWindowsApps("c:\\program files\\windowsapps\\Pkg\\a.exe"))
    }

    @Test
    fun `a launcher elsewhere or absent is not a store install`() {
        assertFalse(StorePackage.isUnderWindowsApps("C:\\Program Files\\ChurchPresenter\\ChurchPresenter.exe"))
        assertFalse(StorePackage.isUnderWindowsApps(null))
    }

    @Test
    fun `auto start keeps the launcher path except for a store install`() {
        assertEquals("C:\\x.exe", AutoStartManager.launcherPath("C:\\x.exe", storeInstall = false))
        assertNull(AutoStartManager.launcherPath("C:\\x.exe", storeInstall = true))
        assertNull(AutoStartManager.launcherPath(null, storeInstall = false))
    }
}
