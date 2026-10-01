package org.churchpresenter.app.churchpresenter

import java.io.File

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import org.churchpresenter.app.churchpresenter.utils.mediaDurationSeconds
import org.churchpresenter.strings.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.BibleBookAbbreviations
import org.churchpresenter.app.churchpresenter.data.asDurationRow
import org.churchpresenter.app.churchpresenter.dialogs.CalendarWindow
import org.churchpresenter.strings.generated.resources.calendar_locate_folder_title
import org.churchpresenter.strings.generated.resources.calendar_choose_logo_title
import org.churchpresenter.strings.generated.resources.calendar_export_title
import org.churchpresenter.strings.generated.resources.calendar_locate_file_title
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.dialogs.CalendarEnrollQrDialog
import org.churchpresenter.app.churchpresenter.server.asInvite
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.server.calendarBibleBooks
import org.churchpresenter.calendar.CalendarCloudSync
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.ui.PreviewSources
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.app.churchpresenter.utils.slideThumbnails
import org.churchpresenter.settings.calendarFolder
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.utils.calendarUsageEvent
import org.churchpresenter.app.churchpresenter.data.BibleBookNames
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import javax.swing.filechooser.FileNameExtensionFilter
import java.nio.file.Files

/** The Calendar Manager window, with everything it reaches back into the app for. */
@Composable
internal fun MainWindowScope.CalendarManagerWindow() {
    with(root) {
        if (showCalendarWindow) {
            // The app's own short book names, in the display language, for the
            // picker's tiles -- by canonical id, 1 to 66.
            val shortBookNames = BibleBookNames.getBookResourceIds().map { stringResource(it) }
            CalendarWindow(
                theme = theme,
                mainWindow = window,
                appDataDirectory = calendarFolder,
                songStorageDirectory = appSettings.songSettings.storageDirectory,
                typicalSongSeconds = { song -> liveDurationLog.median(song.asDurationRow()) },
                host = CalendarHost(
                    // The switch in the calendar's own settings; the same flag the
                    // Server tab's card shows, so the two never disagree.
                    recordUsage = { usage -> UsageEvents.record(calendarUsageEvent(usage)) },
                    cloudSync = CalendarCloudSync(
                        enabled = { appSettings.calendarSync.enabled },
                        setEnabled = { on ->
                            appSettings = appSettings.copy(
                                calendarSync = appSettings.calendarSync.copy(enabled = on),
                            )
                            settingsManager.saveSettings(appSettings)
                        },
                        invitePhone = {
                            coroutineScope.launch {
                                calendarEnrollQr = calendarSync.invitePhone().asInvite(calendarSync)
                            }
                        },
                        nextSyncAt = { calendarSync.nextPullAt.value?.toEpochMilli() },
                    ),
                    // How long a row runs by itself, so a plan does not have
                    // to be timed by hand: a clip's own duration, read from
                    // its header, and a slideshow's count times the interval
                    // it advances at. Null for anything that cannot say.
                    itemRunSeconds = { item -> calendarItemRunSeconds(item) },
                    // Only what has actually happened -- the run of show
                    // shows it beside a plan that says otherwise.
                    measuredSeconds = { item -> liveDurationLog.median(item) },
                    // The run-of-show PDF embeds this. OpenSans covers Cyrillic,
                    // which PDFBox's built-in Helvetica does not — and this app's
                    // song libraries routinely are Cyrillic.
                    pdfFont = ::calendarPdfFont,
                    // Flattened from the loaded primary Bible, so the picker's
                    // book / chapter / verse grids offer exactly what this
                    // translation actually has.
                    bibleBooks = {
                        primaryBibleForInstanceLink
                            ?.let { bible -> calendarBibleBooks(bible, shortBookNames) }
                            .orEmpty()
                    },
                    // The pre-flight check's fix for a moved file: the app's own
                    // chooser, opened where the row still thinks the file is.
                    locateFile = ::locateMissingFile,
                    // A typed reference's book, resolved the way go-live resolves
                    // it -- so "Psalm" against a Russian Bible is not flagged.
                    resolveBookId = { name -> BibleBookAbbreviations.resolveBookId(name) },
                    locateFolder = ::locateMissingFolder,
                    reportError = { context, error ->
                        CrashReporter.reportException(error, context = context)
                    },
                    chooseExportFile = ::chooseCalendarExportFile,
                    chooseImageFile = ::chooseCalendarLogoFile,
                    loadIntoSchedule = this::loadFromCalendar,
                    projectItem = this::projectFromCalendar,
                    blankOutputs = { presenterManager.requestClearDisplay() },
                    currentSchedule = { currentScheduleItems },
                    // The picker's preset previews: the same muted looping
                    // player the backgrounds use, and the deck rasterizer at
                    // thumbnail width.
                    preview = PreviewSources(
                        video = { path, modifier -> LoopingVideoBackground(path, modifier) },
                        slideThumbnails = ::slideThumbnails,
                        scene = { sceneId, modifier ->
                            scenesForInstanceLink.firstOrNull { it.id == sceneId }?.let { scene ->
                                ScenePresenter(modifier = modifier, scene = scene)
                            }
                        },
                    ),
                ),
                // The invite QR belongs to the calendar window, so opening it
                // keeps the calendar in front.
                dialogs = {
                    calendarEnrollQr?.let { invite ->
                        CalendarEnrollQrDialog(
                            invite = invite,
                            onDismiss = { calendarEnrollQr = null },
                        )
                    }
                },
                newServiceFromSchedule = calendarNewServiceFromSchedule,
                onClose = { showCalendarWindow = false }
            )
        }
    }
}

/**
 * How long a row runs by itself: a clip's own duration, a slideshow's count times its interval,
 * else what it has actually taken here -- see LiveDurationLog.
 */
private suspend fun AppRootState.calendarItemRunSeconds(item: ScheduleItem): Int? =
    withContext(Dispatchers.IO) {
        knownRunSeconds(
            item,
            appSettings.pictureSettings.autoScrollInterval,
            appSettings.presentationSettings.autoScrollInterval,
        ) { mediaDurationSeconds(it) } ?: liveDurationLog.median(item)
    }

/** The run-of-show PDF's font. OpenSans covers Cyrillic, which PDFBox's built-in Helvetica does not. */
private fun calendarPdfFont(bold: Boolean): ByteArray? {
    val name = calendarPdfFontName(bold)
    return object {}.javaClass
        .getResourceAsStream("/fonts/$name.ttf")
        ?.use { it.readBytes() }
}

/** The pre-flight check's fix for a moved file: the app's own chooser, opened where the row thinks it is. */
private suspend fun locateMissingFile(missing: File): File? =
    FileChooser.platformInstance.chooseSingle(
        path = missing.parentFile?.toPath()?.takeIf { Files.isDirectory(it) },
        filters = emptyList(),
        title = getString(Res.string.calendar_locate_file_title, missing.name),
        selectDirectory = false,
    )?.toFile()

private suspend fun locateMissingFolder(missing: File): File? =
    FileChooser.platformInstance.chooseSingle(
        path = missing.parentFile?.toPath()?.takeIf { Files.isDirectory(it) },
        filters = emptyList(),
        title = getString(
            Res.string.calendar_locate_folder_title, missing.name,
        ),
        selectDirectory = true,
    )?.toFile()

private suspend fun chooseCalendarExportFile(suggested: String, folder: File?): File? =
    FileChooser.platformInstance.save(
        location = folder?.toPath(),
        suggestedName = suggested,
        filters = listOf(
            FileNameExtensionFilter("PDF Document (*.pdf)", "pdf")
        ),
        title = getString(Res.string.calendar_export_title)
    )?.toFile()

private suspend fun chooseCalendarLogoFile(): File? =
    FileChooser.platformInstance.chooseSingle(
        path = null,
        filters = listOf(
            FileNameExtensionFilter(
                "Images (*.png, *.jpg)", "png", "jpg", "jpeg",
            )
        ),
        title = getString(Res.string.calendar_choose_logo_title),
        selectDirectory = false,
    )?.toFile()
