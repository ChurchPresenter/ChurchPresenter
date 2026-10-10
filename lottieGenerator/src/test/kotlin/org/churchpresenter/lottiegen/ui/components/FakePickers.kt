package org.churchpresenter.lottiegen.ui.components

import java.io.File

/** Answers every chooser with what the test set, and records what was asked. */
internal class FakePickers(
    var file: File? = null,
    var directory: File? = null,
    var saveTo: File? = null,
) : LottieGenPickers {
    val asked = mutableListOf<String>()

    override fun openFile(description: String, vararg extensions: String): File? {
        asked += "file $description ${extensions.joinToString(",")}"
        return file
    }

    override fun openDirectory(): File? {
        asked += "directory"
        return directory
    }

    override fun saveFile(title: String, suggestedName: String): File? {
        asked += "save $suggestedName"
        return saveTo
    }
}
