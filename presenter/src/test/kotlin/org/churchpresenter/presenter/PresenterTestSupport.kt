package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection

internal fun section(vararg lines: String) = LyricSection(type = "verse", lines = lines.toList())
