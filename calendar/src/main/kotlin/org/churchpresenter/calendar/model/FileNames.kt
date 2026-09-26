package org.churchpresenter.calendar.model

/**
 * [name] as a file name every platform will save: the characters Windows refuses -- `\ / : * ? " < > |`
 * and control characters -- become `-`, and trailing dots and spaces, which Windows strips or
 * rejects, go. A service called `Revival: Night 1` is otherwise a name the save dialog will not
 * take, and its Save button appears to do nothing (#651).
 */
fun safeFileName(name: String): String =
    name.map { if (it in UNSAFE_FILE_NAME_CHARS || it.isISOControl()) '-' else it }
        .joinToString("")
        .trimEnd('.', ' ')
        .trim()
        .ifEmpty { "run-of-show" }

private const val UNSAFE_FILE_NAME_CHARS = "\\/:*?\"<>|"
