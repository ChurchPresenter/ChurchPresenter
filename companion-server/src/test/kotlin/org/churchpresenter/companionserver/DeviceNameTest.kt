package org.churchpresenter.companionserver

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Turning what a client says its device is called into something worth showing an operator.
 *
 * Every rule here exists because the alternative to a name is a UUID: nothing a client sends is
 * worth failing a request over, so this decodes what it can and passes through what it cannot.
 */
class DeviceNameTest {

    @Test
    fun `a plain name arrives as it was sent`() {
        assertEquals("Sound desk iPad", decodeDeviceName("Sound desk iPad"))
    }

    @Test
    fun `no name at all is no name, not a blank one`() {
        assertEquals("", decodeDeviceName(null))
        assertEquals("", decodeDeviceName(""))
        assertEquals("", decodeDeviceName("   "))
    }

    @Test
    fun `a name is trimmed`() {
        assertEquals("Stage left", decodeDeviceName("  Stage left  "))
    }

    @Test
    fun `a percent-encoded name is decoded`() {
        // What a client has to send: a header cannot carry these characters raw. OkHttp refuses
        // to send them at all, and anything that does get sent is read back here as ISO-8859-1.
        assertEquals("Серёжин Pixel", decodeDeviceName("%D0%A1%D0%B5%D1%80%D1%91%D0%B6%D0%B8%D0%BD%20Pixel"))
        assertEquals("Sound desk 🎛", decodeDeviceName("Sound%20desk%20%F0%9F%8E%9B"))
    }

    @Test
    fun `a malformed escape keeps the text rather than losing the name`() {
        assertEquals("100%", decodeDeviceName("100%"))
        assertEquals("50%z off", decodeDeviceName("50%z off"))
    }

    @Test
    fun `a plus sign is a plus sign, not a space`() {
        // Names are not form fields; "Booth+1" is a name someone typed.
        assertEquals("Booth+1", decodeDeviceName("Booth+1"))
    }

    @Test
    fun `a control character is dropped, so a name cannot break the line it is drawn on`() {
        assertEquals("SounddeskiPad", decodeDeviceName("Sound\ndesk\riPad"))
    }

    @Test
    fun `what the mobile app encodes is what the operator reads`() {
        // These are the exact strings ChurchPresenter-Mobile's `encodeDeviceName` produces, pinned
        // on both sides: printable ASCII is left alone, so a value mixes raw spaces with escapes.
        assertEquals("Серёжин Pixel", decodeDeviceName("%D0%A1%D0%B5%D1%80%D1%91%D0%B6%D0%B8%D0%BD Pixel"))
        assertEquals("José's iPhone", decodeDeviceName("Jos%C3%A9's iPhone"))
        assertEquals("Sound desk 🎛", decodeDeviceName("Sound desk %F0%9F%8E%9B"))
        assertEquals("100% volume", decodeDeviceName("100%25 volume"))
        assertEquals("Sound desk iPad", decodeDeviceName("Sound desk iPad"))
    }

    @Test
    fun `an absurdly long name is capped rather than refused`() {
        val decoded = decodeDeviceName("x".repeat(500))
        assertEquals(64, decoded.length)
    }
}
