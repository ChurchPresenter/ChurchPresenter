package org.churchpresenter.app.churchpresenter.dialogs.tabs

import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.background_camera_option
import churchpresenter.composeapp.generated.resources.background_color_option
import churchpresenter.composeapp.generated.resources.background_default
import churchpresenter.composeapp.generated.resources.background_follow_default_option
import churchpresenter.composeapp.generated.resources.background_image_option
import churchpresenter.composeapp.generated.resources.background_scope_default
import churchpresenter.composeapp.generated.resources.background_scope_default_lower_third
import churchpresenter.composeapp.generated.resources.background_transparent_option
import churchpresenter.composeapp.generated.resources.background_video_option
import churchpresenter.composeapp.generated.resources.display_lower_third
import churchpresenter.composeapp.generated.resources.full_screen
import churchpresenter.composeapp.generated.resources.gradient_enabled
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The names the Background tab puts on a surface and on a background type.
 *
 * The resource is asserted rather than the rendered English, so the tests say which string is
 * chosen without pinning a wording the translators own — and a type silently falling through to the
 * catch-all is a failure here rather than a mislabelled control on screen.
 */
class BackgroundLabelsTest {

    // ── backgroundTypeLabel ───────────────────────────────────────────────────────────────────

    @Test
    fun `each background type is named by its own string`() {
        assertSame(Res.string.background_color_option, backgroundTypeLabel(Constants.BACKGROUND_COLOR))
        assertSame(Res.string.background_image_option, backgroundTypeLabel(Constants.BACKGROUND_IMAGE))
        assertSame(Res.string.background_video_option, backgroundTypeLabel(Constants.BACKGROUND_VIDEO))
        assertSame(Res.string.background_camera_option, backgroundTypeLabel(Constants.BACKGROUND_CAMERA))
        assertSame(Res.string.background_transparent_option, backgroundTypeLabel(Constants.BACKGROUND_TRANSPARENT))
        assertSame(Res.string.gradient_enabled, backgroundTypeLabel(Constants.BACKGROUND_GRADIENT))
    }

    @Test
    fun `a lower third that follows the default says so`() {
        assertSame(
            Res.string.background_follow_default_option,
            backgroundTypeLabel(Constants.BACKGROUND_FOLLOW_DEFAULT),
        )
    }

    @Test
    fun `a content surface inheriting the default is labelled Default`() {
        assertSame(Res.string.background_default, backgroundTypeLabel(Constants.BACKGROUND_DEFAULT))
    }

    @Test
    fun `an unrecognised type falls back to Default rather than showing nothing`() {
        assertSame(Res.string.background_default, backgroundTypeLabel("Hologram"))
        assertSame(Res.string.background_default, backgroundTypeLabel(""))
    }

    @Test
    fun `the type name is case sensitive, matching the stored constants`() {
        assertSame(Res.string.background_default, backgroundTypeLabel("color"))
    }

    @Test
    fun `every type a surface offers has a name of its own`() {
        val named = BackgroundScope.entries
            .flatMap { it.typeOptions() }
            .distinct()
            .associateWith { backgroundTypeLabel(it) }
        assertEquals(
            emptyList(),
            named.filterValues { it == Res.string.background_default }.keys
                .filterNot { it == Constants.BACKGROUND_DEFAULT },
            "a type on the segmented control must not fall through to the catch-all",
        )
    }

    // ── backgroundGroupLabel and backgroundScopeName ──────────────────────────────────────────

    @Test
    fun `each surface group is named once`() {
        val names = BackgroundScopeGroup.entries.map { backgroundGroupLabel(it) }
        assertEquals(names.size, names.distinct().size, "two groups must not share a heading")
    }

    @Test
    fun `the two default surfaces are named as defaults, not as full screen and band`() {
        assertSame(Res.string.background_scope_default, backgroundScopeName(BackgroundScope.DEFAULT))
        assertSame(
            Res.string.background_scope_default_lower_third,
            backgroundScopeName(BackgroundScope.DEFAULT_LOWER_THIRD),
        )
    }

    @Test
    fun `a content surface is named by which of the two outputs it paints`() {
        assertSame(Res.string.full_screen, backgroundScopeName(BackgroundScope.BIBLE))
        assertSame(Res.string.full_screen, backgroundScopeName(BackgroundScope.SONG))
        assertSame(Res.string.display_lower_third, backgroundScopeName(BackgroundScope.BIBLE_LOWER_THIRD))
        assertSame(Res.string.display_lower_third, backgroundScopeName(BackgroundScope.SONG_LOWER_THIRD))
    }

    @Test
    fun `a surface's name follows its own lower-third flag`() {
        BackgroundScope.entries
            .filter { it.group != BackgroundScopeGroup.DEFAULTS }
            .forEach { scope ->
                val expected = if (scope.lowerThird) Res.string.display_lower_third else Res.string.full_screen
                assertSame(expected, backgroundScopeName(scope), scope.name)
            }
    }

    @Test
    fun `the two defaults are the only surfaces with names of their own`() {
        val contentNames = BackgroundScope.entries
            .filter { it.group != BackgroundScopeGroup.DEFAULTS }
            .map { backgroundScopeName(it) }
            .distinct()
        assertEquals(2, contentNames.size, "a content surface is named full screen or lower third, nothing else")
        assertEquals(false, backgroundScopeName(BackgroundScope.DEFAULT) in contentNames)
    }
}
