package org.churchpresenter.app.churchpresenter.utils

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
internal fun addGuardedShutdownHook(name: String, body: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread(guardedShutdownBody(body), "shutdown-$name"))
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
