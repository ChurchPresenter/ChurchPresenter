package org.churchpresenter.outputguard

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.util.concurrent.CopyOnWriteArrayList

/** A backstop window that only remembers what was done to it. */
class FakeBackstop : Backstop {
    val placed = mutableListOf<Rectangle>()

    /** Written on the event thread by the real loader, so safe to read from a test's. */
    val shown = CopyOnWriteArrayList<BufferedImage>()
    var raised = false
    var disposed = false

    override fun place(bounds: Rectangle) {
        placed += bounds
    }

    override fun show(still: BufferedImage) {
        shown += still
    }

    override fun raise() {
        raised = true
    }

    override fun lower() {
        raised = false
    }

    override fun dispose() {
        disposed = true
    }
}
