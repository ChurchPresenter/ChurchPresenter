package org.churchpresenter.calendar.model

import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File

/**
 * Content paths as the files store them: relative to the store folder when the content is inside
 * it, absolute otherwise.
 *
 * A picture folder, a video and a presentation are all referenced by path, and an absolute path is
 * only true on the machine that wrote it — `/Users/anna/Shared/media/opener.mp4` on one computer
 * is `D:\Shared\media\opener.mp4` on the other. When the content sits inside the calendar folder
 * itself, which is what putting the media in the same synced folder gives, the path is written as
 * `./media/opener.mp4` and resolved against wherever that folder is mounted here. A path outside
 * the folder has nothing to be relative to and is left as it is.
 *
 * Applied at the store boundary and nowhere else: in memory every path is absolute, so the rest of
 * the app never sees the relative form.
 */
private const val RELATIVE_PREFIX = "./"

/** [item] with every content path inside [folder] written relative to it. */
fun ScheduleItem.withPathsRelativeTo(folder: File): ScheduleItem = mapPaths { relativize(it, folder) }

/** [item] with every path written by [withPathsRelativeTo] made absolute against [folder]. */
fun ScheduleItem.withPathsResolvedFrom(folder: File): ScheduleItem = mapPaths { resolve(it, folder) }

fun CalendarDocument.withPathsRelativeTo(folder: File): CalendarDocument =
    mapItems { it.withPathsRelativeTo(folder) }

fun CalendarDocument.withPathsResolvedFrom(folder: File): CalendarDocument =
    mapItems { it.withPathsResolvedFrom(folder) }

fun PresetDocument.withPathsRelativeTo(folder: File): PresetDocument =
    copy(presets = presets.map { it.copy(item = it.item.withPathsRelativeTo(folder)) })

fun PresetDocument.withPathsResolvedFrom(folder: File): PresetDocument =
    copy(presets = presets.map { it.copy(item = it.item.withPathsResolvedFrom(folder)) })

private fun CalendarDocument.mapItems(f: (ScheduleItem) -> ScheduleItem): CalendarDocument = copy(
    services = services.map { it.copy(items = it.items.map(f)) },
    templates = templates.map { it.copy(items = it.items.map(f)) },
)

private fun ScheduleItem.mapPaths(f: (String) -> String): ScheduleItem = when (this) {
    is ScheduleItem.PictureItem -> copy(folderPath = f(folderPath))
    is ScheduleItem.PresentationItem -> copy(filePath = f(filePath))
    // A stream is a URL and stays one; only a local file is a path.
    is ScheduleItem.MediaItem -> if ("://" in mediaUrl) this else copy(mediaUrl = f(mediaUrl))
    is ScheduleItem.CueItem -> copy(payload = payload?.mapPaths(f))
    else -> this
}

private fun relativize(path: String, folder: File): String {
    if (path.isBlank() || path.startsWith(RELATIVE_PREFIX)) return path
    val base = folder.absoluteFile.toPath().normalize()
    val target = File(path).absoluteFile.toPath().normalize()
    if (!target.startsWith(base) || target == base) return path
    return RELATIVE_PREFIX + base.relativize(target).joinToString("/")
}

private fun resolve(path: String, folder: File): String {
    if (!path.startsWith(RELATIVE_PREFIX)) return path
    val relative = path.removePrefix(RELATIVE_PREFIX).replace('/', File.separatorChar)
    return File(folder, relative).absolutePath
}
