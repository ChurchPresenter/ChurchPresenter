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

/**
 * Requests that name the operator's own outputs and profiles — "which profile does Screen 2 use",
 * "what uses Livestream", "which screen is the stage monitor", "make NDI 1 use Stage", "open the
 * Stage profile". They answer from [ResolveContext.outputs] and [ResolveContext.profiles] and point
 * at the control; they never change a setting.
 */
internal fun namedOutputRule(r: Request): Resolution? {
    val context = r.context
    if (context.outputs.isEmpty() && context.profiles.isEmpty()) return null
    val output = context.outputNamedIn(r.text) ?: audienceOutput(r)
    val profile = context.profileNamedIn(r.text)?.takeUnless { output != null && it.name.equals(output.label, true) }
    val asking = r.has(QUESTION)
    val aboutProfile = r.has(PROFILE_WORDS)
    return when {
        output != null && profile != null -> act(pointAtPicker(output, context, profile))
        output != null && aboutProfile && asking ->
            act(say(outputUses(output, context), pointAtPicker(output, context)))
        // Before a profile's name: "stage monitor" would otherwise read as a profile called Stage.
        asking && Vocabulary.STAGE_MONITOR.any { r.text.containsPhrase(it) } && r.has(WHERE_WORDS) ->
            act(stageMonitor(context))
        profile != null && asking && (r.has(USE_WORDS) || r.has(Vocabulary.OUTPUT_WORDS - PROFILE_WORDS)) ->
            act(say(usedBy(profile, context), openProfile(profile, "OUTPUTS")))
        profile != null && r.has(OPEN_WORDS) && leftover(r, profile).all { it in FILLER } ->
            act(openProfile(profile, null))
        else -> null
    }
}

/** "The projector", "the main screen": the first screen output, the one the audience sees. */
private fun audienceOutput(r: Request): KnownOutput? =
    if (AUDIENCE.any { r.text.containsPhrase(it) }) r.context.outputs.firstOrNull { it.kind == "screen" } else null

private fun outputUses(output: KnownOutput, context: ResolveContext): HelperText {
    val profile = context.profiles.find { it.id == output.profileId }
    return if (profile == null) {
        helperText(Res.string.helper_output_no_profile, output.label)
    } else {
        helperText(Res.string.helper_output_uses_profile, output.label, profile.name)
    }
}

private fun usedBy(profile: KnownProfile, context: ResolveContext): HelperText {
    val users = context.outputs.filter { it.profileId == profile.id }
    return if (users.isEmpty()) {
        helperText(Res.string.helper_profile_unused, profile.name)
    } else {
        helperText(Res.string.helper_profile_used_by, profile.name, users.joinToString { it.label })
    }
}

private fun stageMonitor(context: ResolveContext): HelperAction {
    val stage = context.profiles.filter { it.stageMonitor }.map { it.id }.toSet()
    val outputs = context.outputs.filter { it.profileId in stage }
    val first = outputs.firstOrNull() ?: return say(helperText(Res.string.helper_no_stage_monitor), null)
    return say(
        helperText(Res.string.helper_stage_monitor_on, outputs.joinToString { it.label }),
        pointAtPicker(first, context),
    )
}

/** The Projection page, and [output]'s profile picker ringed — saying to pick [wanted] when one is named. */
private fun pointAtPicker(output: KnownOutput, context: ResolveContext, wanted: KnownProfile? = null): HelperAction {
    val hint = if (wanted != null) {
        helperText(Res.string.helper_hint_output_picker_to, wanted.name, output.label)
    } else {
        helperText(Res.string.helper_hint_output_picker, output.label)
    }
    val step = GuideStep(
        GuideTargets.outputProfilePicker(output.kind, output.index),
        hint,
        HelperAction.OpenSettings(SettingsPage.PROJECTION),
    )
    val label = context.profiles.find { it.id == output.profileId }?.name ?: output.label
    return HelperAction.Highlight(GuideTour(listOf(step)), HelperText.Plain(label))
}

private fun openProfile(profile: KnownProfile, page: String?) =
    HelperAction.OpenSettings(SettingsPage.PROFILES, ProfileFocus(profileId = profile.id, page = page))

private fun say(text: HelperText, offer: HelperAction?) = HelperAction.Say(text, offer)

/** The words of the request other than [profile]'s name. */
private fun leftover(r: Request, profile: KnownProfile): List<String> =
    withoutName(r.text, profile.name).split(' ').filter { it.isNotBlank() }

private val QUESTION = setOf("which", "what", "where", "who", "whats", "what's", "does", "is", "show")
private val PROFILE_WORDS = setOf("profile", "profiles")
private val USE_WORDS = setOf("use", "uses", "using", "used")
private val WHERE_WORDS = setOf("which", "where", "what")
private val OPEN_WORDS = setOf("open", "edit", "show", "change", "go", "settings")
private val FILLER = setOf(
    "open", "edit", "show", "change", "go", "to", "me", "the", "my", "profile", "settings", "please", "can", "you",
)
private val AUDIENCE = listOf("projector", "main screen", "audience screen", "the screen the audience sees")

/**
 * [action] with [profile] picked wherever it opens the Profiles page — a tour the rules found for the
 * words of a request that also named a profile ("make the margins bigger on Stage").
 */
internal fun withProfile(action: HelperAction, profile: KnownProfile): HelperAction = when (action) {
    is HelperAction.OpenSettings -> if (action.page == SettingsPage.PROFILES) {
        action.copy(focus = (action.focus ?: ProfileFocus()).copy(profileId = profile.id))
    } else {
        action
    }
    is HelperAction.Highlight -> action.copy(
        tour = GuideTour(
            action.tour.steps.map { step -> step.copy(before = step.before?.let { withProfile(it, profile) }) },
        ),
    )
    else -> action
}
