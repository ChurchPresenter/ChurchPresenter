package org.churchpresenter.app.churchpresenter

/**
 * Runs [block] with `os.name` set to [name], restoring it after. Latches skiko's host OS first --
 * see [TestSingletons.latchSkikoHostOs] for what breaks without it.
 */
internal fun <T> withOsName(name: String, block: () -> T): T {
    TestSingletons.latchSkikoHostOs()
    val previous = System.getProperty("os.name")
    System.setProperty("os.name", name)
    return try {
        block()
    } finally {
        System.setProperty("os.name", previous)
    }
}

/** An OS no camera enumerator claims, so every listing returns empty immediately. */
internal const val OS_WITHOUT_ENUMERATOR = "TestOS"
