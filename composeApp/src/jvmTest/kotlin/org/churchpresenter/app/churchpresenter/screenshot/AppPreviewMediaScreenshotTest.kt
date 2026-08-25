@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.core.models.tabs.Tabs
import kotlin.test.Test

class AppPreviewMediaScreenshotTest {

    /**
     * The media tab with a library loaded — deliberately **not** playing anything.
     *
     * This used to double-click the video and then spin for three seconds
     * (`repeat(60) { … Thread.sleep(50) }`) hoping a frame had decoded before the shot. Three
     * things are wrong with that, and the first one bites hardest:
     *
     * - **It crashes the JVM without the pause.** The double-click starts libvlc, and tearing the
     *   composition down while it is still initialising dies in native code —
     *   `SIGBUS … libvlccore.9.dylib vlc_object_hold`, exit 134. The sleep was load-bearing: it was
     *   not waiting for a frame so much as keeping the process alive long enough for VLC to settle.
     *   So the fix is not to shorten the wait, it is not to start playback.
     * - **`Thread.sleep` is forbidden here** — it asserts on timing rather than behaviour, so a
     *   loaded machine captures whatever happens to be on screen.
     * - **It could never have worked on CI**, where no workflow installs VLC. The pause was three
     *   seconds spent waiting for something that cannot happen there.
     *
     * A decoded frame is also the wrong thing to photograph: which frame arrives depends on how
     * fast the machine got there, so every run would differ and the comparison would report a
     * change on every pull request. Worse, it is a state CI can never reach, so the baseline would
     * only ever be reproducible on a developer's machine. The idle tab is identical everywhere,
     * which is what a baseline has to be.
     */
    @Test
    fun `the media tab`() = appPreview("media", Tabs.MEDIA) {
        waitForIdle()
    }
}
