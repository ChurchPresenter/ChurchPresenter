package org.churchpresenter.app.churchpresenter.composables

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import org.churchpresenter.diagnostics.CrashReporter

/**
 * Initialises the JavaFX toolkit exactly once for the lifetime of the process.
 * Still needed for WebView (WebsitePresenter).
 */
internal fun isJavaFxScreenReconfigRace(throwable: Throwable): Boolean =
    throwable is NullPointerException &&
        throwable.stackTrace.any {
            it.className.startsWith("com.sun.glass.ui.Screen") ||
                it.className.startsWith("com.sun.javafx.tk.quantum.QuantumToolkit")
        }

private object JfxInit {
    @Volatile private var initialised = false

    /** False once the toolkit has been tried and refused to start; see [ensureInit]. */
    @Volatile var available = true
        private set

    fun ensureInit() {
        if (!initialised) {
            synchronized(this) {
                if (!initialised) {
                    initialised = true
                    // Suppress "unnamed module" warning — JavaFX is intentionally loaded
                    // from the classpath in this Compose Desktop build configuration
                    java.util.logging.Logger.getLogger("com.sun.javafx.application.PlatformImpl")
                        .level = java.util.logging.Level.SEVERE
                    // A machine whose JavaFX natives cannot start — no Prism pipeline, a headless
                    // or restricted session, an incomplete install — throws out of the JFXPanel
                    // constructor as `RuntimeException: No toolkit found`, or as a linkage error
                    // from the native load. This runs on `main` before the window exists, so an
                    // escape is a silent failure to launch at all. JavaFX drives nothing the app
                    // cannot do without, so the toolkit is marked unavailable and startup carries
                    // on. Throwable, not Exception: the native failures are Errors. A
                    // VirtualMachineError is rethrown — the JVM is out of headroom, and carrying on
                    // only moves the crash somewhere unrelated.
                    try {
                        JFXPanel()
                    } catch (vme: VirtualMachineError) {
                        throw vme
                    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                        available = false
                        CrashReporter.reportWarning(
                            "JavaFX toolkit unavailable (continuing without it)",
                            throwable = t,
                            tags = mapOf("subsystem" to "javafx_init")
                        )
                        return
                    }
                    // Screen.notifySettingsChanged -> QuantumToolkit.assignScreensAdapters can NPE
                    // deep inside Prism/Glass when the OS reports a display change (monitor
                    // plugged/unplugged) while Prism's GraphicsPipeline isn't fully initialised.
                    // The whole stack is JavaFX-internal with no app frames, so it can't be guarded
                    // with a try/catch at a call site — install a thread-local handler instead that
                    // downgrades just this known race to a warning and defers everything else to
                    // the JVM's default handler.
                    Platform.runLater {
                        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                        Thread.currentThread()
                            .uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
                            if (isJavaFxScreenReconfigRace(throwable)) {
                                CrashReporter.reportWarning(
                                    "JavaFX screen-reconfiguration NPE (suppressed, known Prism/Glass race)",
                                    throwable = throwable,
                                    tags = mapOf("subsystem" to "javafx_screen")
                                )
                            } else {
                                defaultHandler?.uncaughtException(thread, throwable)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Call once from main() to initialise JavaFX before other native toolkits (JCEF). */
fun preWarmJavaFX() = JfxInit.ensureInit()

/**
 * False when the toolkit refused to start. Diagnostics only — it is reported as a crash-service tag
 * so these machines are identifiable, and nothing branches on it, because nothing in the app depends
 * on JavaFX being alive. A future JavaFX consumer would be the thing that has to consult it.
 */
internal fun isJavaFxAvailable(): Boolean = JfxInit.available
