package org.churchpresenter.profiles

/** The once-per-JVM state a test must settle before it fakes `os.name`. */
internal object TestSingletons {
    @Volatile private var skikoLatched = false

    /**
     * Forces skiko to resolve its host OS against the real `os.name`, before any test fakes it:
     * skiko maps `os.name` in a JVM-wide lazy and throws on a name it does not know, after which
     * every later Compose test in the JVM fails to initialise `org.jetbrains.skia.Surface`.
     */
    fun latchSkikoHostOs() {
        if (skikoLatched) return
        synchronized(this) {
            if (skikoLatched) return
            Class.forName("org.jetbrains.skia.Surface")
            skikoLatched = true
        }
    }
}
