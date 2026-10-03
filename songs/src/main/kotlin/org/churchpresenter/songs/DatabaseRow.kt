package org.churchpresenter.songs

interface DatabaseRow {
    fun getString(index: Int): String
    fun getInt(index: Int): Int
}
