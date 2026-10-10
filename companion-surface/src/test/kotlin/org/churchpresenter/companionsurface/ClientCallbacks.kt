package org.churchpresenter.companionsurface

import org.churchpresenter.companionsatellite.CompanionSatelliteClient
import java.lang.reflect.InvocationTargetException

/**
 * Calls the callback a view model handed to [client]'s constructor, held in its private field [name], with
 * [args] — through the lambda's `FunctionN.invoke`, so it is never cast to a function type.
 */
internal fun fireClientCallback(client: CompanionSatelliteClient, name: String, vararg args: Any?) {
    val callback = CompanionSatelliteClient::class.java.getDeclaredField(name).apply { isAccessible = true }
        .get(client)
    val invoke = Class.forName("kotlin.jvm.functions.Function${args.size}")
        .getMethod("invoke", *Array(args.size) { Any::class.java })
    try {
        invoke.invoke(callback, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}
