package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.app.churchpresenter.utils.ServiceFolderConstants
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Creates the material directories without creating or overwriting a service plan. */
object ServiceFolders {
    private val formatter = DateTimeFormatter.ofPattern(ServiceFolderConstants.DATE_PATTERN, Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
    private val datePattern = Regex(ServiceFolderConstants.DATE_REGEX)

    fun formatDate(date: LocalDate): String = date.format(formatter)

    fun isValidDate(value: String): Boolean {
        if (!datePattern.matches(value)) return false
        return try {
            LocalDate.parse(value, formatter).year in 1..9999
        } catch (_: DateTimeParseException) {
            false
        }
    }

    /** Returns the suggested plan path. Repeated calls preserve all existing content. */
    fun create(root: Path, date: String): Path {
        require(isValidDate(date))
        val service = root.toAbsolutePath().resolve(date)
        ServiceFolderConstants.MATERIAL_DIRECTORIES.forEach { name ->
            Files.createDirectories(service.resolve(name))
        }
        return service.resolve(ServiceFolderConstants.PLAN_FILE)
    }
}
