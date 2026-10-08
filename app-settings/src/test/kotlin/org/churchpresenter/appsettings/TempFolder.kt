package org.churchpresenter.appsettings

import java.io.File

/** `newFile`/`newFolder` over a JUnit 5 `@TempDir`, as JUnit 4's `TemporaryFolder` offered them. */
internal class TempFolder(val root: File) {
    fun newFile(name: String): File = File(root, name).apply { createNewFile() }
    fun newFolder(name: String): File = File(root, name).apply { mkdirs() }
}
