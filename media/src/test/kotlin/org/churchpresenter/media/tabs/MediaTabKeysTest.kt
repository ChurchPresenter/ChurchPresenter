package org.churchpresenter.media.tabs

import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.app.churchpresenter.utils.keyDown
import org.churchpresenter.app.churchpresenter.utils.keyUp
import org.churchpresenter.media.FakeMediaOutput
import org.churchpresenter.media.MediaOutput
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.testing.FakeFileChooser
import org.churchpresenter.sharedui.utils.ShortcutMap
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaTabKeysTest {

    private fun scope(output: MediaOutput? = null) = MediaTabScope(
        appSettings = AppSettings(),
        onSettingsChange = {},
        onAddToSchedule = null,
        onSavePreset = null,
        presenterManager = output,
        onInstanceLinkSendProject = null,
        state = MediaTabState(),
        scope = CoroutineScope(Dispatchers.Unconfined),
        sourceTypeItems = emptyList(),
        selectFileLabel = "",
        mediaFilesLabel = "",
        shortcuts = ShortcutMap.DEFAULT,
        wentLive = {},
        fileChooser = FakeFileChooser(answer = null),
    )

    private fun loaded() = MediaViewModel().apply { loadMedia("https://example.org/clip.mp4", "url") }

    @Test
    fun `escape pauses and clears the live output`() {
        val output = FakeMediaOutput().apply { setPresentingMode(Presenting.MEDIA) }
        val vm = loaded().apply { play() }
        assertTrue(scope(output).handleKey(vm, keyDown(Key.Escape)))
        assertFalse(vm.isPlaying)
        assertTrue(output.clearDisplayRequested.value)
    }

    @Test
    fun `escape with no output to clear is not taken`() {
        assertFalse(scope().handleKey(loaded(), keyDown(Key.Escape)))
    }

    @Test
    fun `space toggles a loaded clip`() {
        val vm = loaded()
        assertTrue(scope().handleKey(vm, keyDown(Key.Spacebar)))
        assertTrue(vm.isPlaying)
    }

    @Test
    fun `space with nothing loaded is not taken`() {
        assertFalse(scope().handleKey(MediaViewModel(), keyDown(Key.Spacebar)))
    }

    @Test
    fun `m mutes a loaded clip`() {
        val vm = loaded()
        assertTrue(scope().handleKey(vm, keyDown(Key.M)))
        assertTrue(vm.isMuted)
    }

    @Test
    fun `m with nothing loaded is not taken`() {
        val vm = MediaViewModel()
        assertFalse(scope().handleKey(vm, keyDown(Key.M)))
        assertFalse(vm.isMuted)
    }

    @Test
    fun `any other key is not taken`() {
        assertFalse(scope().handleKey(loaded(), keyDown(Key.Q)))
    }

    @Test
    fun `a key coming up is never taken`() {
        val vm = loaded()
        assertFalse(scope().handleKey(vm, keyUp(Key.Spacebar)))
        assertFalse(vm.isPlaying)
    }
}
