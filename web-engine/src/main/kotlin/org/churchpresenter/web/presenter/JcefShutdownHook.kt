package org.churchpresenter.web.presenter

/**
 * Removes the JVM shutdown hook jcefmaven's `CefAppBuilder.build()` registers.
 *
 * That hook calls `CefApp.dispose()`, which tears Chromium's native context down on the AWT event
 * thread; on macOS that closes JCEF's temporary `NSWindow` off the main thread and AppKit stops the
 * process with SIGTRAP, so every quit ends as a crash and cuts the app's own shutdown hooks short.
 * The app never disposes the engine itself ([CefManager.dispose]): the process exit takes Chromium's
 * helpers with it. Only a hook whose task is jcefmaven's is removed, so a hook another thread adds
 * meanwhile stays. Where the JDK's hook table cannot be read (no `--add-opens` for `java.lang`),
 * nothing is removed and quitting behaves as before.
 */
internal object JcefShutdownHook {
    /**
     * Runs [build] and removes the jcefmaven hooks it registered; returns what [build] returned.
     * [hooks] reads the JVM's table and [isJcefmaven] picks jcefmaven's out of it: seams for tests.
     */
    fun <T> withoutJcefmavenHook(
        hooks: () -> Set<Thread>? = ::registeredHooks,
        isJcefmaven: (Thread) -> Boolean = ::isJcefmavenHook,
        build: () -> T,
    ): T {
        val before = hooks().orEmpty()
        val result = build()
        hooks().orEmpty()
            .filter { it !in before && isJcefmaven(it) }
            .forEach { hook -> runCatching { Runtime.getRuntime().removeShutdownHook(hook) } }
        return result
    }

    /** The JVM's registered shutdown hooks, or null when the JDK's table cannot be read. */
    @Suppress("UNCHECKED_CAST")
    internal fun registeredHooks(): Set<Thread>? = runCatching {
        val table = Class.forName("java.lang.ApplicationShutdownHooks")
        val hooks = table.getDeclaredField("hooks").apply { isAccessible = true }
        synchronized(table) { (hooks.get(null) as Map<Thread, Thread>).keys.toSet() }
    }.getOrNull()

    /** Whether [hook]'s task is a lambda of jcefmaven's, read from the thread's `holder.task`. */
    internal fun isJcefmavenHook(hook: Thread): Boolean = runCatching {
        val holder = Thread::class.java.getDeclaredField("holder").apply { isAccessible = true }.get(hook)
        val task = holder.javaClass.getDeclaredField("task").apply { isAccessible = true }.get(holder)
        task != null && task.javaClass.name.startsWith(JCEFMAVEN_PACKAGE)
    }.getOrDefault(false)

    private const val JCEFMAVEN_PACKAGE = "me.friwi.jcefmaven."
}
