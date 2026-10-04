package org.churchpresenter.schedule

import java.io.File
import java.io.IOException
import java.time.LocalDate
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.filechooser.FileChooser

/** Saves to the current file if known, otherwise prompts like Save As. */
suspend fun ScheduleViewModel.saveSchedule(
    dialogTitle: String = "Save Schedule As",
    fileFilterDescription: String = "Church Presenter Schedule (*.cps)"
) {
    val existing = currentFilePath
    if (existing != null) {
        val file = File(existing)
        file.writeText(encodedSchedule())
        clearAutoSave()
        CrashReporter.breadcrumb("Schedule saved (${file.name})", category = "schedule")
    } else {
        saveScheduleAs(dialogTitle, fileFilterDescription)
    }
}

/**
 * The name a new schedule is offered under: the date first, so a folder of services sorts into
 * the order they were held. [today] is a parameter only so a test does not have to move the
 * clock.
 */
internal fun ScheduleViewModel.suggestedScheduleFileName(today: LocalDate = LocalDate.now()): String =
    "$today-schedule.${Constants.EXTENSION_CPS}"

/** Always opens a save-file dialog and serializes the schedule to a .cps file. */
suspend fun ScheduleViewModel.saveScheduleAs(
    dialogTitle: String = "Save Schedule As",
    fileFilterDescription: String = "Church Presenter Schedule (*.cps)"
) {
    val file = FileChooser.platformInstance.save(
        location = null,
        suggestedName = suggestedScheduleFileName(),
        filters = listOf(FileNameExtensionFilter(fileFilterDescription, Constants.EXTENSION_CPS)),
        title = dialogTitle
    )
    if (file != null) {
        file.writeText(encodedSchedule())
        currentFilePath = file.absolutePathString()
        clearAutoSave()
        CrashReporter.breadcrumb("Schedule saved as (${file.fileName})", category = "schedule")
    }
}

/** Opens an open-file dialog and loads a schedule from a .cps file. */
suspend fun ScheduleViewModel.loadSchedule(
    dialogTitle: String = "Open Schedule",
    fileFilterDescription: String = "Church Presenter Schedule (*.cps)"
) {
    if (_isFollowingRemote.value) return
    val file = FileChooser.platformInstance.chooseSingle(
        path = null,
        filters = listOf(FileNameExtensionFilter(fileFilterDescription, Constants.EXTENSION_CPS)),
        title = dialogTitle,
        selectDirectory = false
    )
    if (file == null || !file.exists()) return
    try {
        val raw = file.readText()
        val jsonText = try { ScheduleCipher.decrypt(raw) } catch (_: Exception) { raw }
        val decoded = decodeSchedule(jsonText)
        replaceSchedule(decoded, filePath = file.absolutePathString())
        CrashReporter.breadcrumb(
            "Schedule opened (${file.fileName}, ${decoded.items.size} items)", category = "schedule"
        )
    } catch (e: CancellationException) {
        // The file dialog this runs behind is cancellable; abandoning it is not a fault.
        throw e
    } catch (e: IOException) {
        Log.warn("Schedule", "Could not read ${file.fileName}: ${e.message}")
        openFailure = ScheduleOpenFailure(file.fileName.toString(), unreadable = true)
    } catch (e: IllegalArgumentException) {
        // Not a schedule: serialization errors are this type. The operator picked the file,
        // so they are the one to tell -- a web page saved under a schedule's name is not a
        // fault in the app (CHURCH-PRESENTER-DESKTOP-9N).
        Log.warn("Schedule", "${file.fileName} is not a schedule: ${e.message?.take(OPEN_FAILURE_DETAIL)}")
        openFailure = ScheduleOpenFailure(file.fileName.toString(), unreadable = false)
    }
}

/** The schedule as it is written to disk: the rows, their notes and their timing. */
internal fun ScheduleViewModel.scheduleFileDocument(): ScheduleFileV2 =
    ScheduleFileV2(items = _scheduleItems.toList(), notes = _notes.toMap(), timing = _timing.toMap())

/** Try new format (v2 with notes and timing), fall back to legacy plain array. */
internal fun ScheduleViewModel.decodeSchedule(jsonText: String): DecodedSchedule =
    try {
        val schedFile = json.decodeFromString(ScheduleFileV2.serializer(), jsonText)
        DecodedSchedule(schedFile.items, schedFile.notes, schedFile.timing)
    } catch (_: Exception) {
        DecodedSchedule(json.decodeFromString(ListSerializer(ScheduleItem.serializer()), jsonText))
    }

/** Clears the schedule and forgets the current file path. */
fun ScheduleViewModel.newSchedule() {
    if (_isFollowingRemote.value) return
    replaceSchedule(DecodedSchedule(emptyList()), filePath = null)
}

/** The schedule serialized and encrypted, as a `.cps` file and the autosave both hold it. */
internal fun ScheduleViewModel.encodedSchedule(): String =
    ScheduleCipher.encrypt(json.encodeToString(ScheduleFileV2.serializer(), scheduleFileDocument()))

internal data class DecodedSchedule(
    val items: List<ScheduleItem>,
    val notes: Map<String, String> = emptyMap(),
    val timing: Map<String, RowTiming> = emptyMap(),
)

@Serializable
internal data class ScheduleFileV2(
    val version: Int = 2,
    val items: List<ScheduleItem>,
    val notes: Map<String, String> = emptyMap(),
    /** How each row runs on its own -- start, length, repeats, end -- keyed by row id. See [RowTiming]. */
    val timing: Map<String, RowTiming> = emptyMap(),
)

/** How much of a decoder's message goes into the log -- the start says what it choked on. */
private const val OPEN_FAILURE_DETAIL = 200

/** A schedule file Open could not use: [fileName], and whether it could not be read at all. */
data class ScheduleOpenFailure(val fileName: String, val unreadable: Boolean)
