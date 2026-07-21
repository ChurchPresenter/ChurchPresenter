package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import java.sql.Connection
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The thin SQLite layer the Mac-format `.sps` songbooks are read through.
 *
 * Everything it returns is a string: rows come back column by column via `getString`, and `getInt`
 * parses whatever that string turned out to be. That is the part worth pinning — a song number
 * stored as text, a null column, or a column that simply isn't there all have to produce something
 * usable rather than an exception, because the files come from another application and this code
 * has no schema to rely on.
 *
 * A real SQLite database is created per test; the driver already ships with the app.
 */
class JdbcDatabaseTest {

    private lateinit var dir: File
    private lateinit var conn: Connection

    @BeforeTest
    fun openDatabase() {
        dir = Files.createTempDirectory("cp-jdbc-test").toFile()
        conn = JdbcDatabase.openConnection(File(dir, "songs.db").absolutePath)
        conn.createStatement().use {
            it.executeUpdate("CREATE TABLE songs (number TEXT, title TEXT, songbook TEXT)")
            it.executeUpdate("INSERT INTO songs VALUES ('1', 'Amazing Grace', 'Hymnal')")
            it.executeUpdate("INSERT INTO songs VALUES ('12', 'How Great Thou Art', 'Hymnal')")
            it.executeUpdate("INSERT INTO songs VALUES ('7', 'Elsewhere', NULL)")
        }
    }

    @AfterTest
    fun closeDatabase() {
        runCatching { conn.close() }
        dir.deleteRecursively()
    }

    private fun rows(result: DatabaseResult): List<DatabaseRow> = result.iterator().asSequence().toList()

    // ── Opening ─────────────────────────────────────────────────────────────────

    @Test
    fun `opening a database gives a usable connection`() {
        assertTrue(!conn.isClosed)
    }

    @Test
    fun `opening a path that does not exist yet creates it`() {
        val fresh = File(dir, "new.db")
        assertTrue(!fresh.exists())

        JdbcDatabase.openConnection(fresh.absolutePath).use {
            it.createStatement().use { stmt -> stmt.executeUpdate("CREATE TABLE t (a TEXT)") }
        }

        assertTrue(fresh.exists(), "SQLite makes the file on first write")
    }

    // ── Reading rows ────────────────────────────────────────────────────────────

    @Test
    fun `a query returns every row`() {
        val result = JdbcDatabase.executeQuery(conn, "SELECT number, title FROM songs ORDER BY title")

        assertEquals(
            listOf("Amazing Grace", "Elsewhere", "How Great Thou Art"),
            rows(result).map { it.getString(1) },
        )
    }

    @Test
    fun `columns are read by position, starting at zero`() {
        val result = JdbcDatabase.executeQuery(conn, "SELECT number, title, songbook FROM songs WHERE number = '1'")

        val row = rows(result).single()
        assertEquals("1", row.getString(0))
        assertEquals("Amazing Grace", row.getString(1))
        assertEquals("Hymnal", row.getString(2))
    }

    @Test
    fun `a number stored as text reads as a number`() {
        val result = JdbcDatabase.executeQuery(conn, "SELECT number FROM songs WHERE title = 'How Great Thou Art'")

        assertEquals(12, rows(result).single().getInt(0), "song numbers are text in these files")
    }

    @Test
    fun `a column that is not a number reads as zero`() {
        val result = JdbcDatabase.executeQuery(conn, "SELECT title FROM songs WHERE number = '1'")

        assertEquals(
            0,
            rows(result).single().getInt(0),
            "a file from another application may put anything in any column",
        )
    }

    @Test
    fun `a null column reads as empty rather than null`() {
        val result = JdbcDatabase.executeQuery(conn, "SELECT songbook FROM songs WHERE number = '7'")

        assertEquals("", rows(result).single().getString(0), "a missing songbook must not become the string 'null'")
        assertEquals(0, rows(result).single().getInt(0))
    }

    @Test
    fun `asking for a column that is not there is not an error`() {
        val result = JdbcDatabase.executeQuery(conn, "SELECT number FROM songs WHERE number = '1'")

        val row = rows(result).single()
        assertEquals("", row.getString(9), "an older file may have fewer columns than the reader expects")
        assertEquals(0, row.getInt(9))
        assertEquals("", row.getString(-1))
    }

    @Test
    fun `a query matching nothing returns no rows`() {
        val result = JdbcDatabase.executeQuery(conn, "SELECT * FROM songs WHERE number = 'nope'")

        assertTrue(rows(result).isEmpty())
        assertNull(result.firstOrNull(), "there is no first row of nothing")
    }

    @Test
    fun `the first row can be taken directly`() {
        val result = JdbcDatabase.executeQuery(conn, "SELECT title FROM songs ORDER BY title")

        assertEquals("Amazing Grace", result.firstOrNull()?.getString(0))
    }

    // ── Parameters ──────────────────────────────────────────────────────────────

    @Test
    fun `a parameter is bound rather than pasted in`() {
        val result = JdbcDatabase.executeQueryParameterized(
            conn,
            "SELECT title FROM songs WHERE number = ?",
            listOf("12"),
        )

        assertEquals("How Great Thou Art", rows(result).single().getString(0))
    }

    @Test
    fun `several parameters are bound in order`() {
        val result = JdbcDatabase.executeQueryParameterized(
            conn,
            "SELECT title FROM songs WHERE songbook = ? AND number = ?",
            listOf("Hymnal", "1"),
        )

        assertEquals("Amazing Grace", rows(result).single().getString(0))
    }

    @Test
    fun `a value that looks like sql is treated as a value`() {
        val result = JdbcDatabase.executeQueryParameterized(
            conn,
            "SELECT title FROM songs WHERE number = ?",
            listOf("1' OR '1'='1"),
        )

        assertTrue(
            rows(result).isEmpty(),
            "binding is what keeps a song number out of the query itself",
        )
    }

    @Test
    fun `a query with no parameters still runs`() {
        val result = JdbcDatabase.executeQueryParameterized(conn, "SELECT title FROM songs ORDER BY title", emptyList())

        assertEquals(3, rows(result).size)
    }
}
