package org.churchpresenter.liveoutput

/**
 * The URL the on-screen Q&A QR code points at.
 *
 * The tunnel URL when there is one, so a phone on mobile data can reach it; the LAN address
 * otherwise.
 */
fun qaQrCodeUrl(tunnelUrl: String, serverUrl: String): String =
    "${tunnelUrl.ifEmpty { serverUrl }}/qa"
