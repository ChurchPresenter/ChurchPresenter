package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import kotlinx.coroutines.CompletableDeferred
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.matchrules.DBusMatchRuleBuilder
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.net.URI
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.io.use

/**
 * A [FileChooser] implementation that uses DBus to communicate with the XDG Desktop Portal's File Chooser API on Linux.
 */
object XdgFileChooser : FileChooser() {

    override suspend fun chooseImpl(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? {
        return openFileChooser(path, filters, title, null, selectDirectory, multiple, DBusFileChooser::OpenFile)
    }

    override suspend fun saveImpl(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String
    ): Path? {
        return openFileChooser(location, filters, title, suggestedName, selectDirectory = false, multiple = false, DBusFileChooser::SaveFile)?.singleOrNull()
    }

    private suspend inline fun openFileChooser(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        suggestedName: String?,
        selectDirectory: Boolean,
        multiple: Boolean,
        dbusMethod: DBusFileChooser.(String, String, Map<String, Variant<*>>) -> DBusPath
    ): List<Path>? = DBusConnectionBuilder.forSessionBus().build().use { conn ->
        val fileChooser = conn.getRemoteObject(
            Constants.DBus.DESKTOP_OBJECT_NAME,
            Constants.DBus.DESKTOP_OBJECT_PATH,
            DBusFileChooser::class.java
        )

        val options = mutableMapOf<String, Variant<*>>()
        options[Constants.DBus.Options.MULTIPLE] = Variant(multiple)
        options[Constants.DBus.Options.DIRECTORY] = Variant(selectDirectory)
        options[Constants.DBus.Options.CURRENT_FOLDER] = Variant(path.toString())
        options[Constants.DBus.Options.FILTERS] = Variant(
            filters.map { filter ->
                DBusFilter(
                    filter.description,
                    filter.extensions.map { ext ->
                        DBusFilter.Pattern(UInt32(0), "*.${ext.asAnyCaseRegex()}")
                    }.toTypedArray()
                )
            }.toTypedArray()
        )
        if (suggestedName != null) {
            options[Constants.DBus.Options.CURRENT_NAME] = Variant(suggestedName)
        }

        val token = Random.nextULong().toString(16)
        options[Constants.DBus.Options.HANDLE_TOKEN] = Variant(token)

        // https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Request.html
        val sender = conn.uniqueName.drop(1).replace('.', '_')
        val requestPath = "/org/freedesktop/portal/desktop/request/$sender/$token"

        val result = CompletableDeferred<List<String>?>()

        val rule = DBusMatchRuleBuilder.create()
            .withType("signal")
            .withInterface("org.freedesktop.portal.Request")
            .withMember("Response")
            .withPath(requestPath)
            .build()
        @Suppress("UNCHECKED_CAST")
        conn.addGenericSigHandler(rule) { signal ->
            val params = signal.parameters
            val response = params[0] as UInt32
            val results = params[1] as Map<String, Variant<*>>
            if (response.toInt() == 0) {
                val uris = results["uris"]?.value as? List<String>
                result.complete(uris?.toList())
            } else {
                result.complete(null)
            }
        }

        fileChooser.dbusMethod("", title, options)

        result.await()?.map { Path.of(URI.create(it)) }
    }

    /**
     * Object used to pass file filter information to the portal via DBus options.
     * See https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.FileChooser.html for details.
     */
    @Suppress("unused")
    private class DBusFilter(
        @Position(0) val name: String,
        @Position(1) val patterns: Array<Pattern>
    ) : Struct() {
        class Pattern(
            @Position(0) val type: UInt32,
            @Position(1) val pattern: String
        ) : Struct()
    }

    @Suppress("FunctionName")
    @DBusInterfaceName("org.freedesktop.portal.FileChooser")
    private interface DBusFileChooser : DBusInterface {
        fun OpenFile(
            parentWindow: String,
            title: String,
            options: Map<String, Variant<*>>
        ): DBusPath

        fun SaveFile(
            parentWindow: String,
            title: String,
            options: Map<String, Variant<*>>,
        ): DBusPath
    }
}

private fun String.asAnyCaseRegex(): String {
    val sb = StringBuilder()
    for (char in this) {
        if (char.isLetter()) {
            sb.append("[").append(char.lowercaseChar()).append(char.uppercaseChar()).append("]")
        } else {
            sb.append(char)
        }
    }
    return sb.toString()
}