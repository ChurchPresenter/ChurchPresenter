package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import kotlinx.coroutines.CompletableDeferred
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.matchrules.DBusMatchRule
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
        return saveSelection(
            openFileChooser(location, filters, title, suggestedName, selectDirectory = false, multiple = false, DBusFileChooser::SaveFile)
        )
    }

    /**
     * The one path a save produced, or null.
     *
     * A save dialog can only name one file, so anything else coming back from the portal is a
     * result that cannot be honoured — treated as no save rather than picking one arbitrarily.
     */
    internal fun saveSelection(paths: List<Path>?): Path? = paths?.singleOrNull()

    /**
     * The filters as the portal wants them: one struct per filter, each carrying glob patterns.
     *
     * The portal matches patterns literally, so every extension is expanded to a case-insensitive
     * glob by [asAnyCaseRegex]. Pattern type `0` marks a glob rather than a MIME type.
     */
    internal fun toDBusFilters(filters: List<FileNameExtensionFilter>): Array<DBusFilter> =
        filters.map { filter ->
            DBusFilter(
                filter.description,
                filter.extensions.map { ext ->
                    DBusFilter.Pattern(UInt32(0), "*.${ext.asAnyCaseRegex()}")
                }.toTypedArray()
            )
        }.toTypedArray()

    /** Everything the portal is told about the dialog to open. */
    internal fun buildOptions(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        suggestedName: String?,
        selectDirectory: Boolean,
        multiple: Boolean,
        token: String
    ): Map<String, Variant<*>> {
        val options = mutableMapOf<String, Variant<*>>()
        options[Constants.DBus.Options.MULTIPLE] = Variant(multiple)
        options[Constants.DBus.Options.DIRECTORY] = Variant(selectDirectory)
        options[Constants.DBus.Options.CURRENT_FOLDER] = Variant(path.toString())
        options[Constants.DBus.Options.FILTERS] = Variant(toDBusFilters(filters))
        // Only a save dialog suggests a name; an open dialog must not send the key at all
        if (suggestedName != null) {
            options[Constants.DBus.Options.CURRENT_NAME] = Variant(suggestedName)
        }
        options[Constants.DBus.Options.HANDLE_TOKEN] = Variant(token)
        return options
    }

    /**
     * The object path the portal will emit its Response signal on.
     *
     * Derived from the connection's unique bus name (`:1.42` → `1_42`) and the handle token, per
     * https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Request.html.
     * Getting this wrong means the handler is registered for a path that never fires and the
     * dialog hangs forever rather than failing.
     */
    internal fun requestPath(uniqueName: String, token: String): String {
        val sender = uniqueName.drop(1).replace('.', '_')
        return "/org/freedesktop/portal/desktop/request/$sender/$token"
    }

    /**
     * Reads the portal's Response signal: `params[0]` is the response code (0 means the operator
     * picked something) and `params[1]` carries the selected `uris`. Anything else is a cancel.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun parseResponse(params: Array<out Any?>): List<String>? {
        val response = params[0] as UInt32
        val results = params[1] as Map<String, Variant<*>>
        if (response.toInt() != 0) return null
        return (results["uris"]?.value as? List<String>)?.toList()
    }

    /** The portal answers with `file://` URIs; callers deal in paths. */
    internal fun toPaths(uris: List<String>?): List<Path>? = uris?.map { Path.of(URI.create(it)) }

    /**
     * The rule that catches the portal's Response signal for [requestPath].
     *
     * Every field has to match what the portal emits — a wrong interface or member name leaves the
     * handler listening for a signal that never comes, and the dialog hangs rather than failing.
     */
    internal fun responseMatchRule(requestPath: String): DBusMatchRule =
        DBusMatchRuleBuilder.create()
            .withType("signal")
            .withInterface("org.freedesktop.portal.Request")
            .withMember("Response")
            .withPath(requestPath)
            .build()

    /**
     * The whole portal request: build the options for [token], work out the path the answer will
     * arrive on, hand both to [ask], and turn the uris it returns into paths.
     *
     * [ask] is a parameter rather than a direct call so the sequence can be exercised without a
     * session bus; in production it registers the signal handler and invokes the portal method.
     */
    internal suspend fun requestPaths(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        suggestedName: String?,
        selectDirectory: Boolean,
        multiple: Boolean,
        uniqueName: String,
        token: String,
        ask: suspend (options: Map<String, Variant<*>>, requestPath: String) -> List<String>?
    ): List<Path>? = toPaths(
        ask(
            buildOptions(path, filters, suggestedName, selectDirectory, multiple, token),
            requestPath(uniqueName, token)
        )
    )

    private suspend inline fun openFileChooser(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        suggestedName: String?,
        selectDirectory: Boolean,
        multiple: Boolean,
        // crossinline: invoked from inside the request lambda below, so it cannot return non-locally
        crossinline dbusMethod: DBusFileChooser.(String, String, Map<String, Variant<*>>) -> DBusPath
    ): List<Path>? = DBusConnectionBuilder.forSessionBus().build().use { conn ->
        val fileChooser = conn.getRemoteObject(
            Constants.DBus.DESKTOP_OBJECT_NAME,
            Constants.DBus.DESKTOP_OBJECT_PATH,
            DBusFileChooser::class.java
        )

        requestPaths(
            path, filters, suggestedName, selectDirectory, multiple,
            conn.uniqueName, Random.nextULong().toString(16)
        ) { options, requestPath ->
            val result = CompletableDeferred<List<String>?>()
            conn.addGenericSigHandler(responseMatchRule(requestPath)) { signal ->
                result.complete(parseResponse(signal.parameters))
            }
            fileChooser.dbusMethod("", title, options)
            result.await()
        }
    }

    /**
     * Object used to pass file filter information to the portal via DBus options.
     * See https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.FileChooser.html for details.
     */
    @Suppress("unused")
    internal class DBusFilter(
        @Position(0) val name: String,
        @Position(1) val patterns: Array<Pattern>
    ) : Struct() {
        internal class Pattern(
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