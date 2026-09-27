package org.churchpresenter.app.churchpresenter.dialogs.tabs

/** One choice of a [RowSegmented]: what it stands for, what it says, and its test handle. */
internal data class RowOption<T>(val value: T, val label: String, val testTag: String? = null)
