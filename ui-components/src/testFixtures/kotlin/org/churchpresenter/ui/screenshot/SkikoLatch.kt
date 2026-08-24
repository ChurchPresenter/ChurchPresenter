package org.churchpresenter.ui.screenshot

@Volatile
private var skikoLatched = false

/**
 * Forces skiko to resolve its host OS and unpack its native library against the **real**
 * `user.home` and `os.name`, before any test swaps either.
 *
 * Two unrelated failures, one cause — a `by lazy` and a one-time unpack that both run inside
 * `org.jetbrains.skia.Surface`'s static initialiser, the class every `runComposeUiTest` touches:
 *
 * - `org.jetbrains.skiko.hostOs` maps `os.name` onto a known OS and throws `Error: Unknown OS <name>`
 *   for anything else. A Compose test composed inside a faked `os.name` that gets there first makes
 *   `Surface` permanently uninitialisable, and every later Compose test in the JVM dies with
 *   `NoClassDefFoundError: Could not initialize class org.jetbrains.skia.Surface` — blamed on
 *   whichever innocent class ran next.
 * - `org.jetbrains.skiko.Library.unpackIfNeeded` extracts the native binary into a cache directory
 *   under `user.home`. A test that redirects `user.home` to a temp dir and is the first to touch
 *   skia unpacks into it; teardown deletes the dir, and every later skia class fails the same way.
 *   It surfaces as an `Error`, so a `catch (e: Exception)` around a decode does not contain it.
 *
 * Whether either bites is pure execution order, which is why CI stays green while running one such
 * class on its own reproduces it every time.
 *
 * **Call as the first line of `@BeforeTest`/`@BeforeClass`**, before any `System.setProperty`.
 * Loading `Surface` is what a Compose test does anyway, so this only moves that cost earlier.
 */
fun latchSkikoNativeLibrary() {
    if (skikoLatched) return
    synchronized(SkikoLatch) {
        if (skikoLatched) return
        // Initialising the class runs the static load that resolves both.
        Class.forName("org.jetbrains.skia.Surface")
        skikoLatched = true
    }
}

private object SkikoLatch
