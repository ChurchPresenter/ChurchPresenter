package org.churchpresenter.lottiegen

/** Which window a standalone launch opens. */
internal enum class LaunchMode { GENERATOR, EDITOR, BAND }

/**
 * The window [args] and the `lottiegen.*` system properties ask for: the band generator over the
 * editor, the generator when neither is asked for.
 */
internal fun launchMode(args: Array<String>, property: (String) -> String? = System::getProperty): LaunchMode {
    val editorMode = args.contains("--editor") || property("lottiegen.editor") == "true"
    val bandMode = args.contains("--band") || property("lottiegen.band") == "true"
    return when {
        bandMode -> LaunchMode.BAND
        editorMode -> LaunchMode.EDITOR
        else -> LaunchMode.GENERATOR
    }
}
