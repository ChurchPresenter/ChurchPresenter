package org.churchpresenter.app.churchpresenter.data

interface DatabaseRow {
    fun getString(index: Int): String
    fun getInt(index: Int): Int
}
