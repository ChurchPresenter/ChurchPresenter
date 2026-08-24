package org.churchpresenter.stt

/**
 * What the Live Captions tab needs from the screens, and nothing more.
 *
 * The tab used to take `PresenterManager` and a `(Presenting) -> Unit`, which meant it could reach
 * every output in the app and name every kind of content there is — two symbols that both live in
 * `:composeApp`. It needs neither. It has to know whether the captions are the thing currently on
 * screen, so the Go Live button can disable itself, and it has to be able to put them there.
 *
 * [isLive] is deliberately a `Boolean` rather than the `Presenting` enum: the tab never asks *what*
 * is live when it is not the captions, so exporting the enum would widen this port to the whole
 * app's content model for no gain. `:composeApp` implements this over `PresenterManager` in
 * `PresenterSttOutput`.
 */
interface SttOutput {

    /** Whether the captions are what the outputs are currently showing. */
    val isLive: Boolean

    /** Put the captions on the screens. */
    fun goLive()
}
