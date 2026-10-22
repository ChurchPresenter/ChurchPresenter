package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import org.churchpresenter.diagnostics.Log
import java.awt.Window

/**
 * Whether [throwable] is Compose Multiplatform's disposed-owner race: a mouse drag still being
 * delivered to a layout node whose owner was disposed under it -- a press that takes something
 * live and switches the tab while the button is still held. The event is for a node that is gone,
 * so dropping it loses nothing; letting it through closes the window and the app.
 *
 * Matched on the type and the exact message `RootNodeOwner.measureAndLayout`'s `require` throws.
 * The message is Compose's own, fixed in its code and never translated, and it is the only thing
 * that separates this from any other `IllegalArgumentException`.
 */
internal fun isDisposedOwnerRace(throwable: Throwable): Boolean =
    throwable is IllegalArgumentException && throwable.message == DISPOSED_OWNER_MESSAGE

/**
 * What a window does with an exception from its event handling: the disposed-owner race
 * ([isDisposedOwnerRace]) is logged and dropped, and everything else goes to [otherwise] -- the
 * window's usual handler, which shows the error, closes the window and rethrows it to the
 * uncaught-exception handler that reports it.
 */
internal fun onWindowException(throwable: Throwable, otherwise: (Throwable) -> Unit) {
    if (isDisposedOwnerRace(throwable)) {
        Log.warn(TAG, "Dropped an input event for a layout already disposed")
    } else {
        otherwise(throwable)
    }
}

/** [delegate]'s handler for every window, with the disposed-owner race dropped first ([onWindowException]). */
@OptIn(ExperimentalComposeUiApi::class)
internal fun toleratingDisposedOwnerRace(delegate: WindowExceptionHandlerFactory): WindowExceptionHandlerFactory =
    object : WindowExceptionHandlerFactory {
        override fun exceptionHandler(window: Window): WindowExceptionHandler {
            val usual = delegate.exceptionHandler(window)
            return object : WindowExceptionHandler {
                override fun onException(throwable: Throwable) = onWindowException(throwable, usual::onException)
            }
        }
    }

private const val DISPOSED_OWNER_MESSAGE = "RootNodeOwner is already disposed"
private const val TAG = "WindowExceptions"
