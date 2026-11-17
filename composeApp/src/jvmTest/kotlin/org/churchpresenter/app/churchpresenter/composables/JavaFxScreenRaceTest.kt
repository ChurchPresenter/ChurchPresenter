package org.churchpresenter.app.churchpresenter.composables

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JavaFxScreenRaceTest {

    private fun throwableFrom(vararg classNames: String, cause: Throwable = NullPointerException()) =
        cause.also { t ->
            t.stackTrace = classNames.map { StackTraceElement(it, "method", "File.java", 1) }.toTypedArray()
        }

    @Test
    fun `an NPE from the glass screen layer is the known race`() {
        val throwable = throwableFrom("com.sun.glass.ui.Screen", "javafx.application.Platform")

        assertTrue(isJavaFxScreenReconfigRace(throwable))
    }

    @Test
    fun `an NPE from the quantum toolkit is the known race`() {
        val throwable = throwableFrom("com.sun.javafx.tk.quantum.QuantumToolkit")

        assertTrue(isJavaFxScreenReconfigRace(throwable))
    }

    @Test
    fun `a nested glass frame anywhere in the stack still counts`() {
        val throwable = throwableFrom(
            "java.util.ArrayList",
            "com.sun.glass.ui.Screen\$1",
            "java.lang.Thread",
        )

        assertTrue(isJavaFxScreenReconfigRace(throwable))
    }

    @Test
    fun `an NPE from the app's own code is not the known race`() {
        val throwable = throwableFrom("org.churchpresenter.app.churchpresenter.MainKt")

        assertFalse(isJavaFxScreenReconfigRace(throwable))
    }

    @Test
    fun `a negative screen size from the glass layer is the same race`() {
        // CHURCH-PRESENTER-DESKTOP-1CY: Rectangle2D refused a screen that had just been unplugged.
        val throwable = throwableFrom(
            "javafx.geometry.Rectangle2D",
            "com.sun.glass.ui.Screen",
            cause = IllegalArgumentException("Both width and height must be >= 0"),
        )

        assertTrue(isJavaFxScreenReconfigRace(throwable))
    }

    @Test
    fun `an illegal argument from the app's own code is not the known race`() {
        val throwable = throwableFrom(
            "org.churchpresenter.app.churchpresenter.MainKt",
            cause = IllegalArgumentException("bad"),
        )

        assertFalse(isJavaFxScreenReconfigRace(throwable))
    }

    @Test
    fun `a different exception from the same javafx frame is not the known race`() {
        val throwable = throwableFrom(
            "com.sun.glass.ui.Screen",
            cause = IllegalStateException("toolkit not initialised"),
        )

        assertFalse(isJavaFxScreenReconfigRace(throwable), "only the NPE is the race worth suppressing")
    }

    @Test
    fun `an NPE with no stack trace at all is not the known race`() {
        assertFalse(isJavaFxScreenReconfigRace(throwableFrom()))
    }

    @Test
    fun `a frame that merely mentions screen elsewhere is not the known race`() {
        val throwable = throwableFrom("com.example.glass.ui.Screen", "org.other.QuantumToolkit")

        assertFalse(isJavaFxScreenReconfigRace(throwable))
    }
}
