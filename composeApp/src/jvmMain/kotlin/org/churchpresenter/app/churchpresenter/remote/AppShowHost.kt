package org.churchpresenter.app.churchpresenter.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.findLottiePresetFile
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.clearGroup
import org.churchpresenter.app.churchpresenter.viewmodel.clearLayer
import org.churchpresenter.app.churchpresenter.viewmodel.layerForName
import org.churchpresenter.app.churchpresenter.viewmodel.propsOnAir
import org.churchpresenter.app.churchpresenter.viewmodel.setPropOn
import org.churchpresenter.app.churchpresenter.viewmodel.showLowerThird
import org.churchpresenter.app.churchpresenter.viewmodel.showMessage
import org.churchpresenter.app.churchpresenter.viewmodel.toggleProp
import org.churchpresenter.atem.AtemClient
import org.churchpresenter.atem.AtemKey
import org.churchpresenter.calendar.model.isContentRow
import org.churchpresenter.calendar.model.nextContentRow
import org.churchpresenter.calendar.model.previousContentRow
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.fillMessage
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.showcontrol.Action
import org.churchpresenter.showcontrol.MediaCommand
import org.churchpresenter.showcontrol.ShowHost
import java.io.File
import java.time.LocalTime

/**
 * What the show host reaches beyond the live output: the root's schedule, media and devices. Lambdas,
 * every one defaulted, as [org.churchpresenter.calendar.CalendarHost] is, so a test fills in only
 * what it drives.
 */
internal data class ShowOutlets(
    val rows: () -> List<ScheduleItem> = { emptyList() },
    /** The schedule row on air, or selected -- where next and previous count from. */
    val currentRowId: () -> String? = { null },
    /** Puts [item] on air from the schedule, played [plays] times -- as a fired cue does. */
    val goLive: (item: ScheduleItem, plays: Int) -> Unit = { _, _ -> },
    /** Cues [item] on Preview. */
    val toPreview: (item: ScheduleItem) -> Unit = {},
    val media: (MediaCommand) -> Unit = {},
    val obsScene: (String) -> Unit = {},
    /** Presses a Companion button; false when no surface answers to it. */
    val companion: (Action.CompanionPress) -> Boolean = { false },
    /** Runs [block] on the configured ATEM switcher. */
    val atem: suspend (block: suspend (AtemClient) -> Unit) -> Unit = {},
    val macro: (String) -> List<Action>? = { null },
    val log: (message: String) -> Unit = {},
)

/**
 * Carries out show-control actions on the app (`docs/SHOW_CONTROL.md`, Actions): the live output
 * through [presenterManager], saved things -- clear groups, messages, props -- by id or by name
 * from [settings], and everything else through [outlets]. An action that names something that is
 * not there throws [IllegalArgumentException], which the runner reports and passes over.
 */
@Suppress("TooManyFunctions") // One call per action, as ShowHost has.
internal class AppShowHost(
    private val presenterManager: PresenterManager,
    private val settings: () -> AppSettings,
    private val outlets: ShowOutlets,
) : ShowHost {

    override suspend fun goLive(action: Action.GoLive) = outlets.goLive(itemOf(action.item, action.rowId), action.plays)

    override suspend fun toPreview(action: Action.ToPreview) {
        require(settings().projectionSettings.previewModeEnabled) { "Preview mode is off" }
        outlets.toPreview(itemOf(action.item, action.rowId))
    }

    override suspend fun take(layer: String) {
        require(layer.isBlank()) { "Taking one layer at a time is not supported yet" }
        presenterManager.previewBus.take()
    }

    override suspend fun clear(layer: String) {
        val named = layerForName(layer) ?: Layer.entries.firstOrNull { it.name.equals(layer.trim(), ignoreCase = true) }
        presenterManager.clearLayer(requireNotNull(named) { "No layer called $layer" })
    }

    override suspend fun clearAll() = presenterManager.requestClearDisplay()

    override suspend fun clearGroup(group: String) {
        val found = settings().clearGroups.named(group, { it.id }, { it.name })
        presenterManager.clearGroup(requireNotNull(found) { "No clear group called $group" })
    }

    override suspend fun message(action: Action.Message) {
        val template = action.template.takeIf { it.isNotBlank() }?.let { wanted ->
            requireNotNull(settings().messageTemplates.named(wanted, { it.id }, { it.name })) {
                "No saved message called $wanted"
            }
        }
        val text = template?.let { fillMessage(it.text, action.tokens) } ?: action.text
        require(text.isNotBlank()) { "A message needs words" }
        val duration = action.durationSeconds ?: template?.durationSeconds
        presenterManager.showMessage(Cue.Message(text, template?.name, duration))
        presenterManager.setShowPresenterWindow(true)
    }

    override suspend fun prop(action: Action.Prop) {
        val prop = requireNotNull(settings().props.named(action.prop, { it.id }, { it.name })) {
            "No prop called ${action.prop}"
        }
        action.on?.let { presenterManager.setPropOn(prop.id, it) } ?: presenterManager.toggleProp(prop.id)
        if (prop.id in presenterManager.propsOnAir) presenterManager.setShowPresenterWindow(true)
    }

    override suspend fun lowerThird(preset: String) {
        val folder = File(settings().streamingSettings.lowerThirdFolder)
        val file = withContext(Dispatchers.IO) {
            findLottiePresetFile(folder.listFiles()?.toList(), preset.trim(), preset.trim())?.takeIf { it.isFile }
        }
        requireNotNull(file) { "No lower third called $preset" }
        val json = withContext(Dispatchers.IO) { file.readText() }
        presenterManager.previewBus.showLowerThird(json, false, -1f, 0L, file.nameWithoutExtension)
    }

    override suspend fun timer(action: Action.Timer) {
        val announcements = settings().announcementsSettings
        when (action.mode) {
            TimerModes.COUNT_UP -> presenterManager.startAnnouncementCountUp(0)
            TimerModes.CLOCK -> {
                val until = requireNotNull(runCatching { LocalTime.parse(action.until.trim()) }.getOrNull()) {
                    "A clock timer needs a time, HH:mm"
                }
                presenterManager.startAnnouncementSpecificTime(until.hour, until.minute, until.second)
            }
            TimerModes.CLOCK_DISPLAY -> presenterManager.startAnnouncementClockDisplay(announcements.liveClockFormat)
            TimerModes.DURATION -> presenterManager.startAnnouncementCountdown(
                action.seconds.coerceAtLeast(0),
                announcements.timerExpiredText,
            )
            else -> throw IllegalArgumentException("No timer mode called ${action.mode}")
        }
        presenterManager.setAnnouncementTickerLive(true)
        presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
        presenterManager.setShowPresenterWindow(true)
    }

    override suspend fun media(command: MediaCommand) = outlets.media(command)

    override suspend fun obsScene(scene: String) = outlets.obsScene(scene)

    override suspend fun atemKey(action: Action.AtemKey) = outlets.atem {
        val key = AtemKey(useDsk = action.downstream, mixEffect = action.mixEffect, keyer = action.keyer)
        it.setKeyOnAir(key, action.on)
    }

    override suspend fun atemMacro(index: Int) = outlets.atem { it.runMacro(index) }

    override suspend fun companion(action: Action.CompanionPress) =
        require(outlets.companion(action)) { "No Companion surface for ${action.connection}" }

    override suspend fun next() {
        val rows = outlets.rows()
        val current = outlets.currentRowId()
        val target = if (current == null) rows.firstOrNull { it.isContentRow() } else rows.nextContentRow(current)
        target?.let { outlets.goLive(it, 1) }
    }

    override suspend fun previous() {
        outlets.currentRowId()?.let { outlets.rows().previousContentRow(it) }?.let { outlets.goLive(it, 1) }
    }

    override fun macro(name: String): List<Action>? = outlets.macro(name)

    override fun reportError(action: Action, error: Throwable) {
        outlets.log("Show control could not run $action: ${error.message}")
    }

    /** [item] itself, or the schedule row [rowId] names. */
    private fun itemOf(item: ScheduleItem?, rowId: String): ScheduleItem =
        item ?: requireNotNull(outlets.rows().firstOrNull { it.id == rowId }) { "No schedule row $rowId" }
}

/** The first of [this] whose id is [wanted], else the first whose name is, in any case. */
private fun <T> List<T>.named(wanted: String, id: (T) -> String, name: (T) -> String): T? =
    firstOrNull { id(it) == wanted } ?: firstOrNull { name(it).equals(wanted.trim(), ignoreCase = true) }
