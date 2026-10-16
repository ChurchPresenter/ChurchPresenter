package org.churchpresenter.media.composables

import uk.co.caprica.vlcj.factory.MediaPlayerFactory

data class VlcAudioDevice(val id: String, val description: String)

/**
 * Drops VLC's own "use whatever the system is using" entry, which it reports with an **empty**
 * device id and a description in VLC's language rather than the app's ("Default" in English).
 *
 * The app already offers that choice itself — the first item in the dropdown, labelled with the
 * localized `audio_output_default` string — and stores it as an empty `audioOutputDeviceId`.
 * Keeping VLC's copy as well put two rows meaning the same thing in the menu, and because the
 * stored id is `""` it also *matched* VLC's entry, so the closed button showed VLC's untranslated
 * description instead of the app's string on every machine whose VLC reports a default device
 * (which is every machine with a working audio output).
 */
internal fun withoutVlcDefaultDevice(devices: List<VlcAudioDevice>): List<VlcAudioDevice> =
    devices.filter { it.id.isNotBlank() }

/** Lists available audio output devices via VLCJ. */
fun listVlcAudioDevices(): List<VlcAudioDevice> {
    if (!isVlcAvailable) return emptyList()
    return try {
        val factory = MediaPlayerFactory()
        val mp = factory.mediaPlayers().newMediaPlayer()
        val devices = mp.audio().outputDevices()
            .map { VlcAudioDevice(it.deviceId, it.longName) }
        mp.release()
        factory.release()
        withoutVlcDefaultDevice(devices)
    } catch (_: Throwable) { emptyList() }
}
