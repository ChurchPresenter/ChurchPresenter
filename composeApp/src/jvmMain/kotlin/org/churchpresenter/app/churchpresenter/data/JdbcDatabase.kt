package org.churchpresenter.app.churchpresenter.data

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

// JDBC-backed implementations of your placeholder interfaces
class JdbcDatabaseRow(private val values: List<String>) : DatabaseRow {
    override fun getString(index: Int): String = values.getOrNull(index) ?: ""
    override fun getInt(index: Int): Int = values.getOrNull(index)?.toIntOrNull() ?: 0
}

class JdbcDatabaseResult(private val rows: List<JdbcDatabaseRow>) : DatabaseResult {
    override fun iterator(): Iterator<DatabaseRow> = rows.iterator()
    override fun firstOrNull(): DatabaseRow? = rows.firstOrNull()
}

// Utility to run a query and return DatabaseResult
object JdbcDatabase {
    // Open connection (call once on app start)
    fun openConnection(path: String): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:$path")
    }

    fun executeQuery(conn: Connection, sql: String): DatabaseResult {
        val rows = mutableListOf<JdbcDatabaseRow>()
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery(sql)
            val meta = rs.metaData
            val cols = meta.columnCount
            while (rs.next()) {
                val vals = (1..cols).map { i -> rs.getString(i) ?: "" }
                rows.add(JdbcDatabaseRow(vals))
            }
            rs.close()
        }
        return JdbcDatabaseResult(rows)
    }
}