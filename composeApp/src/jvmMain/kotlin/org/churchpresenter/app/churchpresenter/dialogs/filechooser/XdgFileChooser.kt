package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import kotlinx.coroutines.CompletableDeferred
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.IDisconnectCallback
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.matchrules.DBusMatchRule
import org.freedesktop.dbus.matchrules.DBusMatchRuleBuilder
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.io.use

/**
 * A [FileChooser] implementation that uses DBus to communicate with the XDG Desktop Portal's File Chooser API on Linux.
 *
 * Everything here depends on someone else's process: a session bus, and a desktop portal that
 * implements the FileChooser interface. Neither is guaranteed to be there — a session with no bus
 * name yet answers `getUniqueName()` with an index error, a desktop with no portal has nothing to
 * call — so every portal request goes through [withNativeDialog] and lands on the Swing dialog
 * rather than taking the app down with it.
 */
object XdgFileChooser : FileChooser() {

    override suspend fun chooseImpl(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? = withNativeDialog(
        context = "XdgFileChooser.chooseImpl",
        attempt = {
            openFileChooser(path, filters, title, null, selectDirectory, multiple, DBusFileChooser::OpenFile)
        },
        fallback = { SwingFileChooser.fallbackChoose(path, filters, title, selectDirectory, multiple) }
    )

    override suspend fun saveImpl(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String
    ): Path? = withNativeDialog(
        context = "XdgFileChooser.saveImpl",
        attempt = {
            saveSelection(
                openFileChooser(location, filters, title, suggestedName, selectDirectory = false, multiple = false, DBusFileChooser::SaveFile)
            )
        },
        fallback = { SwingFileChooser.fallbackSave(location, suggestedName, filters, title) }
    )

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
    /**
     * The connection's unique bus name, or a failure that says what went wrong.
     *
     * dbus-java reads it off a list it has not necessarily filled — a connection whose `Hello`
     * never completed answers with `IndexOutOfBoundsException: Index 0 out of bounds for length
     * 0`, which reaches Sentry as an ArrayList error with nothing about D-Bus in it. There is no
     * name to build a request path from either way, so both shapes of "no name" become one
     * refusal, and the caller falls back to the Swing dialog.
     */
    internal fun uniqueNameOf(read: () -> String?): String {
        val name = try {
            read()
        } catch (_: IndexOutOfBoundsException) {
            null
        }
        return name?.takeIf { it.isNotBlank() }
            ?: error("The D-Bus session connection has no unique name; the desktop portal is unreachable")
    }

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
     * The request path to listen on in addition to the predicted one, or null when there is
     * nothing to add.
     *
     * [requestPath] is a prediction of what the portal will name the Request object, and the
     * portal is free to name it something else — it returns the real one from `OpenFile`/
     * `SaveFile`, and the documentation says to use that. Listening only on the prediction means
     * the Response signal arrives somewhere nobody is listening and the dialog waits for ever, so
     * a handle that differs is listened on as well rather than instead: the prediction has to be
     * registered before the call to avoid missing a fast answer, and both cannot be wrong.
     */
    internal fun extraResponsePath(predicted: String, handle: String?): String? =
        handle?.takeIf { it.isNotBlank() && it != predicted }

    /**
     * A callback that ends the wait if the session bus goes away.
     *
     * The Response signal is the only thing that finishes a portal request, and a bus that has
     * disconnected will never deliver one — the dialog is gone from the operator's screen too, so
     * the honest answer is a cancel rather than a wait nothing can end.
     */
    internal fun cancelOnDisconnect(response: CompletableDeferred<List<String>?>): IDisconnectCallback =
        object : IDisconnectCallback {
            override fun clientDisconnect() { response.complete(null) }
            override fun requestedDisconnect(code: Int?) { response.complete(null) }
            override fun disconnectOnError(exception: IOException) { response.complete(null) }
        }

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
    ): List<Path>? {
        // Created before the connection so a bus that drops can end the wait — see cancelOnDisconnect.
        val response = CompletableDeferred<List<String>?>()
        return DBusConnectionBuilder.forSessionBus()
            .withDisconnectCallback(cancelOnDisconnect(response))
            .build()
            .use { conn ->
                val fileChooser = conn.getRemoteObject(
                    Constants.DBus.DESKTOP_OBJECT_NAME,
                    Constants.DBus.DESKTOP_OBJECT_PATH,
                    DBusFileChooser::class.java
                )

                requestPaths(
                    path, filters, suggestedName, selectDirectory, multiple,
                    uniqueNameOf { conn.uniqueName }, Random.nextULong().toString(16)
                ) { options, requestPath ->
                    conn.addGenericSigHandler(responseMatchRule(requestPath)) { signal ->
                        response.complete(parseResponse(signal.parameters))
                    }
                    // An unanswered method call fails on dbus-java's own reply timeout, so only the
                    // wait below is open-ended — as it must be, since it is the operator deciding.
                    val handle = fileChooser.dbusMethod("", title, options)
                    extraResponsePath(requestPath, handle?.path)?.let { actualPath ->
                        conn.addGenericSigHandler(responseMatchRule(actualPath)) { signal ->
                            response.complete(parseResponse(signal.parameters))
                        }
                    }
                    response.await()
                }
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