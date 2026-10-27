package org.churchpresenter.signage.core

import java.security.MessageDigest

/**
 * The version of a manifest: the SHA-256 of its encoding. Only the side that writes a manifest
 * computes it; a player treats the version it is sent as an opaque string.
 */
fun Manifest.version(): String = sha256Hex(SignageJson.encodeToString(Manifest.serializer(), this).toByteArray())

/** Lower-case hex SHA-256, the form every file hash in the protocol takes. */
fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Whether [text] looks like a hash this protocol uses: 64 lower-case hex digits. */
fun isSha256Hex(text: String): Boolean =
    text.length == SHA256_HEX_LENGTH && text.all { it in '0'..'9' || it in 'a'..'f' }

private const val SHA256_HEX_LENGTH = 64
