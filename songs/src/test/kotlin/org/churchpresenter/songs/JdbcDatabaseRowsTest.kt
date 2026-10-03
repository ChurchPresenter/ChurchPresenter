package org.churchpresenter.songs

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class JdbcDatabaseRowsTest {

    private lateinit var dir: File

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-jdbc-coverage").toFile()
    }

    @AfterTest
    fun deleteDir() {
        dir.deleteRecursively()
    }

    @Test
    fun `a row reads its values by position`() {
        val row = JdbcDatabaseRow(listOf("5", "five"))
        assertEquals("five", row.getString(1))
        assertEquals(5, row.getInt(0))
    }

    @Test
    fun `a row past its last value reads empty and zero`() {
        val row = JdbcDatabaseRow(listOf("5"))
        assertEquals("", row.getString(3))
        assertEquals(0, row.getInt(3))
    }

    @Test
    fun `an empty result has no first row and nothing to iterate`() {
        val result = JdbcDatabaseResult(emptyList())
        assertNull(result.firstOrNull())
        assertFalse(result.iterator().hasNext())
    }

    @Test
    fun `a result iterates its rows in order`() {
        val result = JdbcDatabaseResult(listOf(JdbcDatabaseRow(listOf("a")), JdbcDatabaseRow(listOf("b"))))
        assertEquals(listOf("a", "b"), result.map { it.getString(0) })
        assertEquals("a", result.firstOrNull()?.getString(0))
    }

    @Test
    fun `a null parameter is bound as null and reads back empty`() {
        JdbcDatabase.openConnection(File(dir, "db.sqlite").absolutePath).use { c ->
            c.createStatement().use { it.executeUpdate("CREATE TABLE t (a TEXT, b TEXT)") }
            c.prepareStatement("INSERT INTO t VALUES (?, ?)").use { st ->
                st.setString(1, "x"); st.setString(2, null); st.executeUpdate()
            }
            val result = JdbcDatabase.executeQueryParameterized(c, "SELECT b FROM t WHERE b IS ?", listOf(null))
            assertEquals(listOf(""), result.map { it.getString(0) })
        }
    }

    @Test
    fun `a parameterized query matching nothing has no rows`() {
        JdbcDatabase.openConnection(File(dir, "db2.sqlite").absolutePath).use { c ->
            c.createStatement().use { it.executeUpdate("CREATE TABLE t (a INTEGER)") }
            val result = JdbcDatabase.executeQueryParameterized(c, "SELECT a FROM t WHERE a = ?", listOf(1))
            assertNull(result.firstOrNull())
        }
    }
}
