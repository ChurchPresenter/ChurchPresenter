package org.churchpresenter.settings

import java.io.File

enum class ResourceDirectory { BIBLE, SONGS, PICTURES, LOWER_THIRD, PRESENTATIONS, MEDIA, SCHEDULE, CALENDAR, SIGNAGE }

fun AppSettings.resourceDirectories(section: ResourceDirectory): List<String> {
    val primary = when (section) {
        ResourceDirectory.BIBLE -> bibleSettings.storageDirectory
        ResourceDirectory.SONGS -> songSettings.storageDirectory
        ResourceDirectory.PICTURES -> pictureSettings.storageDirectory
        ResourceDirectory.LOWER_THIRD -> streamingSettings.lowerThirdFolder
        ResourceDirectory.PRESENTATIONS -> presentationStorageDirectory
        ResourceDirectory.MEDIA -> mediaStorageDirectory
        ResourceDirectory.SCHEDULE -> scheduleStorageDirectory
        ResourceDirectory.CALENDAR -> calendarStorageDirectory
        ResourceDirectory.SIGNAGE -> signageStorageDirectory
    }
    return (listOf(primary) + additionalResourceDirectories[section].orEmpty())
        .map(String::trim).filter(String::isNotEmpty).distinctBy { File(it).absoluteFile.normalize() }
}

fun AppSettings.withPrimaryResourceDirectory(
    section: ResourceDirectory, path: String, previousDefault: String,
): AppSettings {
    val selected = File(path).absoluteFile.normalize()
    val remaining = (listOf(previousDefault) + additionalResourceDirectories[section].orEmpty())
        .filter { it.isNotBlank() && File(it).absoluteFile.normalize() != selected }.distinct()
    val changed = when (section) {
        ResourceDirectory.BIBLE -> copy(bibleSettings = bibleSettings.copy(storageDirectory = path))
        ResourceDirectory.SONGS -> copy(songSettings = songSettings.copy(storageDirectory = path))
        ResourceDirectory.PICTURES -> copy(pictureSettings = pictureSettings.copy(storageDirectory = path))
        ResourceDirectory.LOWER_THIRD -> copy(streamingSettings = streamingSettings.copy(lowerThirdFolder = path))
        ResourceDirectory.PRESENTATIONS -> copy(presentationStorageDirectory = path)
        ResourceDirectory.MEDIA -> copy(mediaStorageDirectory = path)
        ResourceDirectory.SCHEDULE -> copy(scheduleStorageDirectory = path)
        ResourceDirectory.CALENDAR -> copy(calendarStorageDirectory = path)
        ResourceDirectory.SIGNAGE -> copy(signageStorageDirectory = path)
    }
    return changed.copy(additionalResourceDirectories = additionalResourceDirectories + (section to remaining))
}

fun AppSettings.withAdditionalResourceDirectory(section: ResourceDirectory, path: String): AppSettings {
    val normalized = path.trim()
    if (normalized.isEmpty() || resourceDirectories(section).any {
            File(it).absoluteFile.normalize() == File(normalized).absoluteFile.normalize()
        }) return this
    return copy(additionalResourceDirectories = additionalResourceDirectories +
        (section to (additionalResourceDirectories[section].orEmpty() + normalized)))
}
