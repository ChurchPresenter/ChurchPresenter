package org.churchpresenter.appsettings

/**
 * The argument a relaunched app is started with: the pid of the app it replaces, which it waits for
 * before taking the single-instance lock, so the old one has let go of the lock and its ports.
 */
const val REPLACING_PID_ARGUMENT = "--replacing-pid="

/** [base] with [pid] as the process to replace, in place of any it already carried. */
fun replacingCommand(base: List<String>, pid: Long): List<String> =
    base.filterNot { it.startsWith(REPLACING_PID_ARGUMENT) } + "$REPLACING_PID_ARGUMENT$pid"

/**
 * How Settings starts this app again: the packaged launcher where there is one, else this JVM's own
 * command line, waiting for this process. Windows reports no arguments for a process, so the
 * launcher's path is the only command that always starts the app there.
 */
internal fun relaunchCommand(
    appPath: String? = System.getProperty(APP_PATH_PROPERTY),
    info: ProcessHandle.Info = ProcessHandle.current().info(),
    pid: Long = ProcessHandle.current().pid(),
): List<String> {
    val base = if (appPath != null) {
        listOf(appPath)
    } else {
        listOf(info.command().orElse(System.getProperty("java.home") + "/bin/java")) +
            info.arguments().orElse(emptyArray())
    }
    return replacingCommand(base, pid)
}

/** Where jpackage tells a packaged app the path of its own launcher. */
private const val APP_PATH_PROPERTY = "jpackage.app-path"
