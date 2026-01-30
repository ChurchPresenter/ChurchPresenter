package org.churchpresenter.app.churchpresenter.data

data class SongItem(
    val number: String,
    val title: String,
    val songbook: String = "Песнь Возрождения 3300",
    val tune: String = "",
    val author: String = "",
    val composer: String = "",
    val lyrics: List<String> = emptyList()
)
