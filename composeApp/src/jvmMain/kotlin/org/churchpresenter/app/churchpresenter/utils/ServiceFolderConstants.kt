package org.churchpresenter.app.churchpresenter.utils

/** On-disk layout shared by the service preparation utility and its preview. */
object ServiceFolderConstants {
    const val DATE_PATTERN = "dd.MM.uuuu"
    const val DATE_REGEX = "[0-9]{2}\\.[0-9]{2}\\.[0-9]{4}"
    const val PLAN_FILE = "plan.cps"
    val MATERIAL_DIRECTORIES = listOf("Pictures", "Presentations", "Media")
}
