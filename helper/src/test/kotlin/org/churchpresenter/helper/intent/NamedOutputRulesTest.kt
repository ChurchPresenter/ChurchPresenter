package org.churchpresenter.helper.intent

import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.GuideStep
import org.churchpresenter.helper.action.GuideTour
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.helperText
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.ProfileFocus
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_hint_output_picker
import org.churchpresenter.strings.generated.resources.helper_hint_output_picker_to
import org.churchpresenter.strings.generated.resources.helper_no_stage_monitor
import org.churchpresenter.strings.generated.resources.helper_output_no_profile
import org.churchpresenter.strings.generated.resources.helper_output_uses_profile
import org.churchpresenter.strings.generated.resources.helper_profile_unused
import org.churchpresenter.strings.generated.resources.helper_profile_used_by
import org.churchpresenter.strings.generated.resources.helper_stage_monitor_on
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class NamedOutputRulesTest {

    private val main = KnownProfile("d", "Default")
    private val live = KnownProfile("l", "Livestream")
    private val stage = KnownProfile("s", "Stage", stageMonitor = true)
    private val spare = KnownProfile("x", "Spare")
    private val context = ResolveContext(
        language = "en",
        profiles = listOf(main, live, stage, spare),
        outputs = listOf(
            KnownOutput("Screen 1", "screen", 0, "d"),
            KnownOutput("Screen 2", "screen", 1, "s"),
            KnownOutput("NDI Feed", "ndi", 0, "l"),
            KnownOutput("OMT 1", "omt", 0, null),
        ),
    )

    private fun rule(text: String, context: ResolveContext = this.context): HelperAction? {
        val normal = normalize(text)
        val resolution = namedOutputRule(Request(normal, normal.split(' '), context, raw = text)) ?: return null
        return assertIs<Resolution.Act>(resolution).action
    }

    private fun said(text: String) = assertIs<HelperAction.Say>(rule(text))

    private fun picker(kind: String, index: Int, hint: HelperText, label: String) = HelperAction.Highlight(
        GuideTour(
            listOf(
                GuideStep(
                    GuideTargets.outputProfilePicker(kind, index),
                    hint,
                    HelperAction.OpenSettings(SettingsPage.PROJECTION),
                ),
            ),
        ),
        HelperText.Plain(label),
    )

    @Test
    fun `naming an output and a profile points at that output's picker`() {
        assertEquals(
            picker("ndi", 0, helperText(Res.string.helper_hint_output_picker_to, "Stage", "NDI Feed"), "Livestream"),
            rule("make NDI Feed use Stage"),
        )
    }

    @Test
    fun `asked which profile an output uses, it says so and offers the picker`() {
        val screen = said("which profile does Screen 1 use")
        assertEquals(helperText(Res.string.helper_output_uses_profile, "Screen 1", "Default"), screen.text)
        assertEquals(
            picker("screen", 0, helperText(Res.string.helper_hint_output_picker, "Screen 1"), "Default"),
            screen.offer,
        )
        val none = said("what profile is OMT 1 on")
        assertEquals(helperText(Res.string.helper_output_no_profile, "OMT 1"), none.text)
        assertEquals(HelperText.Plain("OMT 1"), assertIs<HelperAction.Highlight>(none.offer).label)
    }

    @Test
    fun `the projector is the first screen`() {
        val projector = said("which profile does the projector use")
        assertEquals(helperText(Res.string.helper_output_uses_profile, "Screen 1", "Default"), projector.text)
    }

    @Test
    fun `asked what uses a profile, it names the outputs, or says none does`() {
        val used = said("what uses Livestream")
        assertEquals(helperText(Res.string.helper_profile_used_by, "Livestream", "NDI Feed"), used.text)
        assertEquals(
            HelperAction.OpenSettings(SettingsPage.PROFILES, ProfileFocus(profileId = "l", page = "OUTPUTS")),
            used.offer,
        )
        assertEquals(helperText(Res.string.helper_profile_unused, "Spare"), said("which outputs use Spare").text)
    }

    @Test
    fun `asked where the stage monitor is, it names the output, or says there is none`() {
        val on = said("which screen is the stage monitor")
        assertEquals(helperText(Res.string.helper_stage_monitor_on, "Screen 2"), on.text)
        val noStage = context.copy(profiles = listOf(main, live))
        val off = assertIs<HelperAction.Say>(rule("where is the stage monitor", noStage))
        assertEquals(helperText(Res.string.helper_no_stage_monitor), off.text)
        assertNull(off.offer)
    }

    @Test
    fun `naming a profile to open opens it, and anything more is for the other rules`() {
        assertEquals(
            HelperAction.OpenSettings(SettingsPage.PROFILES, ProfileFocus(profileId = "l")),
            rule("open the Livestream profile"),
        )
        assertNull(rule("open the Livestream margins"))
    }

    @Test
    fun `with no outputs or profiles, or nothing named, the rule stays out`() {
        assertNull(rule("which profile does Screen 1 use", ResolveContext(language = "en")))
        assertNull(rule("make the background blue"))
    }

    @Test
    fun `a profile named like an output is read as the output`() {
        val same = context.copy(profiles = context.profiles + KnownProfile("n", "NDI Feed"))
        val asked = assertIs<HelperAction.Say>(rule("which profile does NDI Feed use", same))
        assertEquals(helperText(Res.string.helper_output_uses_profile, "NDI Feed", "Livestream"), asked.text)
    }

    @Test
    fun `a profile named with a request opens that profile wherever the request goes`() {
        val profiles = HelperAction.OpenSettings(SettingsPage.PROFILES, ProfileFocus(page = "SONGS"))
        assertEquals(
            HelperAction.OpenSettings(SettingsPage.PROFILES, ProfileFocus(profileId = "l", page = "SONGS")),
            withProfile(profiles, live),
        )
        val bare = HelperAction.OpenSettings(SettingsPage.PROFILES)
        assertEquals(
            HelperAction.OpenSettings(SettingsPage.PROFILES, ProfileFocus(profileId = "l")),
            withProfile(bare, live),
        )
        val elsewhere = HelperAction.OpenSettings(SettingsPage.SYSTEM)
        assertEquals(elsewhere, withProfile(elsewhere, live))
        val tour = HelperAction.Highlight(
            GuideTour(
                listOf(
                    GuideStep(GuideTargets.PROFILE_MARGINS, HelperText.Plain("here"), profiles),
                    GuideStep(GuideTargets.SETTINGS_BUTTON, HelperText.Plain("there")),
                ),
            ),
        )
        val steps = assertIs<HelperAction.Highlight>(withProfile(tour, live)).tour.steps
        assertEquals(withProfile(profiles, live), steps[0].before)
        assertNull(steps[1].before)
        assertEquals(HelperAction.ClearOutput, withProfile(HelperAction.ClearOutput, live))
    }
}
