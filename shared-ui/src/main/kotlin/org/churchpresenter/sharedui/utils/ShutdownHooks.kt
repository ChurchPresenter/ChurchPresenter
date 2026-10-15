package org.churchpresenter.sharedui.utils

/**
 * Registers a JVM shutdown hook whose body cannot turn a clean exit into a reported crash.
 *
 * A shutdown hook runs on its own thread, so anything it throws goes to the default uncaught
 * handler — which is `CrashReporter`. The app has already closed successfully by then, and the
 * user sees nothing, but a `fatal` event is filed as though it had crashed. That is what happened
 * to the resource census hook: it was the first thing to ask for a class, the class could not be
 * loaded during exit, and a clean shutdown was reported as a crash.
 *
 * `Throwable` and not `Exception`: the observed failure was a `NoClassDefFoundError`, and the
 * shutdown path is exactly where `Error`s of that shape appear. Failing to release something while
 * the process is ending is not worth a report — the OS reclaims it either way.
 *
 * [name] names the thread, so a hang during shutdown is identifiable in a thread dump.
 */
fun addGuardedShutdownHook(name: String, body: () -> Unit): GuardedShutdownHook {
    val thread = Thread(guardedShutdownBody(body), "shutdown-$name")
    Runtime.getRuntime().addShutdownHook(thread)
    return GuardedShutdownHook(thread)
}

/**
 * A hook [addGuardedShutdownHook] registered. The JVM keeps its body until exit, and with it
 * everything the body reaches — so an owner that ends before the JVM does [remove]s it, or every
 * instance it ever made stays reachable for the life of the process.
 */
class GuardedShutdownHook internal constructor(private val thread: Thread) {
    /** Takes the hook back; false when it was already gone or the JVM is already shutting down. */
    fun remove(): Boolean = try {
        Runtime.getRuntime().removeShutdownHook(thread)
    } catch (_: IllegalStateException) {
        false
    }
}

/**
 * [body] wrapped so nothing escapes it — the part of [addGuardedShutdownHook] that can be run in a
 * test, since registering a real hook only pays off when the JVM ends.
 */
internal fun guardedShutdownBody(body: () -> Unit): Runnable = Runnable {
    try {
        body()
    } catch (_: Throwable) {
        // Shutting down already; there is nowhere useful for this to go.
    }
}
