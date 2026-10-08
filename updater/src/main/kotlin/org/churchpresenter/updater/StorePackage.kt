package org.churchpresenter.updater

// Microsoft Store (MSIX) installs live under Program Files\WindowsApps, where the install folder is
// read-only, updates come from the Store and HKCU writes are virtualized per package.
object StorePackage {
    val isInstalled: Boolean = isUnderWindowsApps(System.getProperty("jpackage.app-path"))

    internal fun isUnderWindowsApps(appPath: String?): Boolean =
        appPath?.contains("\\WindowsApps\\", ignoreCase = true) == true
}
