package org.churchpresenter.settingspages

internal object TestSingletons {

    @Volatile private var skikoLatched = false

    fun latchSkikoNativeLibrary() {
        if (skikoLatched) return
        synchronized(this) {
            if (skikoLatched) return
            Class.forName("org.jetbrains.skia.Surface")
            skikoLatched = true
        }
    }
}
