package org.churchpresenter.app.churchpresenter.data

// Placeholder interface for database results
// You'll need to implement this based on your database framework
interface DatabaseResult : Iterable<DatabaseRow> {
    fun firstOrNull(): DatabaseRow?
}
