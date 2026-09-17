package org.churchpresenter.calendar.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The short date the design puts in a sheet title, a template's second line and the copy banner. */
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** `20 Sept 2026`, in the machine's own locale. */
internal fun shortDate(date: LocalDate): String = date.format(SHORT_DATE.withLocale(Locale.getDefault()))
