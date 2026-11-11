package org.churchpresenter.media.composables

import uk.co.caprica.vlcj.factory.MediaPlayerFactory

/**
 * The one libVLC every player in this run plays through.
 *
 * Making a `MediaPlayerFactory` is `libvlc_new`, which loads every VLC plugin: a second or more on
 * Windows. Each player component used to make its own, inside composition, so opening a video or
 * moving it to another output stalled the event thread for that long -- and the software player
 * made a second one beside it for its frame surface. Players built on this one only make a player
 * (`libvlc_media_player_new`), which is quick, and releasing a player leaves the factory alone:
 * vlcj releases a component's factory only when the component made it.
 *
 * The arguments are vlcj's own component defaults ([VLCJ_COMPONENT_ARGS]), so a player behaves as
 * it did with a factory of its own. The factory lives for the run.
 */
internal object SharedVlcFactory {
    @Volatile private var factory: MediaPlayerFactory? = null

    /** The factory, made the first time it is asked for; throws when libVLC will not load. */
    @Synchronized
    fun get(): MediaPlayerFactory = factory ?: MediaPlayerFactory(VLCJ_COMPONENT_ARGS).also { factory = it }
}

/**
 * What vlcj 4.8's `EmbeddedMediaPlayerComponent` and `CallbackMediaPlayerComponent` both hand the
 * factory they make for themselves (`MediaPlayerComponentDefaults.EMBEDDED_MEDIA_PLAYER_ARGS`,
 * which vlcj keeps package-private). Check it against vlcj when the version moves.
 */
internal val VLCJ_COMPONENT_ARGS = listOf(
    "--video-title=vlcj video output",
    "--no-snapshot-preview",
    "--quiet",
    "--intf=dummy",
)

/**
 * Finds and loads VLC on a thread of its own. Call it at startup, after [vlcCustomPath] is set,
 * so the first video opened -- and the first composition asking [isVlcAvailable] -- finds it loaded.
 */
fun warmVlcInBackground() {
    Thread({ isVlcAvailable }, "vlc-warm-up").apply { isDaemon = true }.start()
}
