package org.churchpresenter.liveoutput

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.sharedui.models.Presenting

/**
 * A page drawn on a remote device's canvas, as the outputs draw it. Part of [PresenterManager].
 *
 * The page arrives as a picture after every edit, already decoded off the event thread. Each one
 * replaces the last in a single state write, so an output keeps the previous page on screen until
 * the next is ready -- never a blank frame between two strokes.
 */
interface LiveDrawing {
    /** The latest picture of the page, or null while none has arrived. */
    val drawingFrame: State<ImageBitmap?>

    /** The page's own background, which fills the screen around a page of another shape. */
    val drawingPaper: State<Color>

    /**
     * How many pictures have arrived since the drawing last went live -- each is a new
     * [org.churchpresenter.liveshow.Cue.Drawing].
     */
    val drawingRevision: State<Long>

    /** Puts [frame] up in place of the last one, framed by [paper]. */
    fun showDrawingFrame(frame: ImageBitmap, paper: Color)

    /** Forgets the page, releasing its picture -- the drawing has left the screen. */
    fun clearDrawing()
}

internal class LiveDrawingState(private val context: PresenterContext) : LiveDrawing {

    private val _drawingFrame = mutableStateOf<ImageBitmap?>(null)
    override val drawingFrame: State<ImageBitmap?> = _drawingFrame

    private val _drawingPaper = mutableStateOf(Color.Black)
    override val drawingPaper: State<Color> = _drawingPaper

    private val _drawingRevision = mutableLongStateOf(0L)
    override val drawingRevision: State<Long> = _drawingRevision

    override fun showDrawingFrame(frame: ImageBitmap, paper: Color) {
        val first = _drawingFrame.value == null
        _drawingFrame.value = frame
        _drawingPaper.value = paper
        _drawingRevision.longValue += 1
        // Live state changes when a drawing's first page arrives; each page after it is the same
        // drawing still up, and announcing every stroke would flood the companion broadcast.
        if (first) context.notify(Presenting.DRAWING)
    }

    override fun clearDrawing() {
        if (_drawingFrame.value == null) return
        _drawingFrame.value = null
        _drawingPaper.value = Color.Black
        _drawingRevision.longValue = 0L
    }
}
