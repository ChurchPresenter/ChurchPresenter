@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import org.churchpresenter.ui.recolor

/**
 * The QR Code source: seven kinds of payload, two of which change the shape of the panel.
 *
 * Picking a type does more than store it — it **overwrites the content** with a worked example of
 * that payload's syntax, because a QR code carrying an email address that still reads `https://…`
 * scans to nothing useful. That prefill is a seven-branch `when`, and it deliberately makes one
 * exception: WiFi keeps whatever content it had, since a WiFi code is assembled from the SSID and
 * password fields instead of from the content box. Both the six prefills and that one exception are
 * pinned, as is the panel's other shape change — WiFi swapping the content box for four credential
 * controls of its own.
 */
class SourcePropertiesQRCodeTest {

    /** Ordinals of the panel's fields. Which exist depends on the payload type. */
    private object Field {
        const val CONTENT = 6      // every type but WiFi
        const val WIFI_SSID = 6
        const val WIFI_PASSWORD = 7
    }

    /** Ordinals of the panel's checkboxes; WiFi inserts its own before the transparency box. */
    private object Check {
        const val TRANSPARENT = 0
        const val WIFI_HIDDEN = 0
        const val WIFI_TRANSPARENT = 1
    }

    /** Every payload type: the value stored, the label shown, and the content picking it prefills. */
    private val types = listOf(
        Triple("url", "URL", "https://example.com"),
        Triple("text", "Text", "Your text here"),
        Triple("email", "Email", "mailto:name@example.com"),
        Triple("phone", "Phone", "tel:+1234567890"),
        Triple("sms", "SMS", "smsto:+1234567890:Message"),
        Triple(
            "vcard", "vCard",
            "BEGIN:VCARD\nVERSION:3.0\nFN:Name\nTEL:+1234567890\nEMAIL:name@example.com\nEND:VCARD",
        ),
    )

    private fun wifi() = Fixture.qr().copy(contentType = "wifi", wifiSsid = "Sanctuary", wifiPassword = "peace123")

    // ── What the panel displays ───────────────────────────────────────────────

    @Test
    fun `the section is headed and every control captioned`() = sourcePanel(Fixture.qr()) { _ ->
        listOf("TYPE", "CONTENT", "FOREGROUND", "Transparent background", "BACKGROUND", "ERROR CORRECTION")
            .forEach { caption ->
                onNodeWithText(caption).assertExists("\"$caption\" must caption a control on the QR panel")
            }
        onNodeWithText(Label.QRCODE).assertIsDisplayed()
    }

    @Test
    fun `a URL code adds one field and one checkbox to the header`() = sourcePanel(Fixture.qr()) { _ ->
        textFields().assertCountEquals(7)
        checkboxes().assertCountEquals(1)
    }

    @Test
    fun `every stored value is shown by the control that owns it`() {
        val configured = Fixture.qr().copy(
            content = "https://church.example", foregroundColor = "#003366",
            backgroundColor = "#EEEEEE", errorCorrection = "Q",
        )
        sourcePanel(configured) { _ ->
            assertFieldShows("https://church.example", "the content field")
            onNodeWithText("#003366").assertExists("the foreground colour reads out its hex")
            onNodeWithText("#EEEEEE").assertExists("the background colour reads out its hex")
            // Not "H": the header's own Height field is captioned H, so a level of Q is unambiguous.
            assertEquals(1, countOf("Q"), "the error correction dropdown names the stored level")
        }
    }

    // ── Payload type ──────────────────────────────────────────────────────────

    @Test
    fun `the type dropdown names each stored type`() {
        (types.map { it.first to it.second } + ("wifi" to "WiFi")).forEach { (stored, shown) ->
            sourcePanel(Fixture.qr().copy(contentType = stored)) { _ ->
                assertEquals(1, countOf(shown), "$stored must read as \"$shown\"")
            }
        }
    }

    @Test
    fun `a type this build does not know reads as URL`() {
        sourcePanel(Fixture.qr().copy(contentType = "geo")) { _ ->
            onNodeWithText("URL").assertExists("an unrecognised type must name a real option")
            assertEquals(0, countOf("geo"), "and must not show itself")
        }
    }

    @Test
    fun `the type dropdown offers all seven payloads`() = sourcePanel(Fixture.qr()) { _ ->
        openDropdown(showing = "URL")

        (types.map { it.second } + "WiFi").forEach { option ->
            // URL is both the closed selector's text and a menu entry; the others appear once.
            val expected = if (option == "URL") 2 else 1
            assertEquals(expected, countOf(option), "\"$option\" must be offered")
        }
    }

    @Test
    fun `choosing a type stores it and prefills the content with that payload's syntax`() {
        types.filter { it.first != "url" }.forEach { (stored, label, prefill) ->
            sourcePanel(Fixture.qr()) { get ->
                chooseFromDropdown(showing = "URL", option = label)

                val source = get() as SceneSource.QRCodeSource
                assertEquals(stored, source.contentType, "\"$label\" must store $stored")
                assertEquals(prefill, source.content, "and must prefill an example of its own syntax")
            }
        }
    }

    @Test
    fun `choosing URL again restores the URL example`() {
        sourcePanel(Fixture.qr().copy(contentType = "phone", content = "tel:+15550100")) { get ->
            chooseFromDropdown(showing = "Phone", option = "URL")

            val source = get() as SceneSource.QRCodeSource
            assertEquals("url", source.contentType)
            assertEquals("https://example.com", source.content)
        }
    }

    @Test
    fun `choosing WiFi keeps the content it had, because WiFi is built from its own fields`() {
        sourcePanel(Fixture.qr().copy(content = "https://keep.me")) { get ->
            chooseFromDropdown(showing = "URL", option = "WiFi")

            val source = get() as SceneSource.QRCodeSource
            assertEquals("wifi", source.contentType)
            assertEquals(
                "https://keep.me", source.content,
                "WiFi is the one type whose content is not overwritten",
            )
        }
    }

    // ── Content ───────────────────────────────────────────────────────────────

    @Test
    fun `typing content stores it and nothing else`() = sourcePanel(Fixture.qr()) { get ->
        typeField(Field.CONTENT, "https://church.example/give")

        assertEquals(
            Fixture.qr().copy(content = "https://church.example/give"), get(),
            "the content box may write only the content",
        )
        assertFieldShows("https://church.example/give", "the content field after typing")
    }

    @Test
    fun `the content can be cleared`() = sourcePanel(Fixture.qr()) { get ->
        typeField(Field.CONTENT, "")

        assertEquals("", (get() as SceneSource.QRCodeSource).content)
    }

    // ── WiFi ──────────────────────────────────────────────────────────────────

    @Test
    fun `a WiFi code swaps the content box for four credential controls`() = sourcePanel(wifi()) { _ ->
        listOf("NETWORK NAME (SSID)", "PASSWORD", "ENCRYPTION", "Hidden Network").forEach { caption ->
            onNodeWithText(caption).assertExists("\"$caption\" must caption a WiFi control")
        }
        assertEquals(0, countOf("CONTENT"), "and the content box is gone")
        textFields().assertCountEquals(8)
        checkboxes().assertCountEquals(2)
    }

    @Test
    fun `a WiFi code shows its stored credentials`() = sourcePanel(wifi()) { _ ->
        assertFieldShows("Sanctuary", "the SSID field")
        assertFieldShows("peace123", "the password field")
        onNodeWithText("WPA").assertExists("the encryption dropdown names the stored scheme")
    }

    @Test
    fun `typing an SSID stores it`() = sourcePanel(wifi()) { get ->
        typeField(Field.WIFI_SSID, "Guest WiFi")

        val source = get() as SceneSource.QRCodeSource
        assertEquals("Guest WiFi", source.wifiSsid)
        assertEquals("peace123", source.wifiPassword, "and leaves the password alone")
    }

    @Test
    fun `typing a password stores it`() = sourcePanel(wifi()) { get ->
        typeField(Field.WIFI_PASSWORD, "hunter2")

        val source = get() as SceneSource.QRCodeSource
        assertEquals("hunter2", source.wifiPassword)
        assertEquals("Sanctuary", source.wifiSsid, "and leaves the SSID alone")
    }

    @Test
    fun `the encryption dropdown offers every scheme`() = sourcePanel(wifi()) { _ ->
        openDropdown(showing = "WPA")

        listOf("WPA2", "WPA3", "WEP", "None").forEach { option ->
            assertEquals(1, countOf(option), "\"$option\" must be offered")
        }
        assertEquals(2, countOf("WPA"), "WPA is both the closed selector and a menu entry")
    }

    @Test
    fun `choosing an encryption scheme stores it`() {
        listOf("WPA2", "WPA3", "WEP", "None").forEach { scheme ->
            sourcePanel(wifi()) { get ->
                chooseFromDropdown(showing = "WPA", option = scheme)

                assertEquals(scheme, (get() as SceneSource.QRCodeSource).wifiEncryption)
            }
        }
    }

    @Test
    fun `Hidden Network is off out of the box`() = sourcePanel(wifi()) { _ ->
        checkboxes()[Check.WIFI_HIDDEN].assertIsOff()
    }

    @Test
    fun `ticking Hidden Network stores the flag`() = sourcePanel(wifi()) { get ->
        toggleCheckbox(Check.WIFI_HIDDEN)

        assertEquals(
            wifi().copy(wifiHidden = true), get(),
            "ticking the box may change only that flag",
        )
        checkboxes()[Check.WIFI_HIDDEN].assertIsOn()
    }

    @Test
    fun `unticking Hidden Network turns it back off`() {
        sourcePanel(wifi().copy(wifiHidden = true)) { get ->
            toggleCheckbox(Check.WIFI_HIDDEN)

            assertEquals(false, (get() as SceneSource.QRCodeSource).wifiHidden)
        }
    }

    @Test
    fun `the transparency box on a WiFi code is the second one, not the first`() = sourcePanel(wifi()) { get ->
        toggleCheckbox(Check.WIFI_TRANSPARENT)

        val source = get() as SceneSource.QRCodeSource
        assertEquals(true, source.transparentBackground, "the second box is transparency")
        assertEquals(false, source.wifiHidden, "and the hidden-network flag is untouched")
    }

    // ── Colours ───────────────────────────────────────────────────────────────

    @Test
    fun `recolouring the foreground stores the new hex`() = sourcePanel(Fixture.qr()) { get ->
        recolor(fromHex = "#000000", toHex = "#004400")

        val source = get() as SceneSource.QRCodeSource
        assertEquals("#004400", source.foregroundColor)
        assertEquals("#FFFFFF", source.backgroundColor, "and the background is untouched")
    }

    @Test
    fun `recolouring the background stores the new hex`() = sourcePanel(Fixture.qr()) { get ->
        recolor(fromHex = "#FFFFFF", toHex = "#FFEECC")

        val source = get() as SceneSource.QRCodeSource
        assertEquals("#FFEECC", source.backgroundColor)
        assertEquals("#000000", source.foregroundColor, "and the foreground is untouched")
    }

    @Test
    fun `transparency is off out of the box, so a background colour is offered`() =
        sourcePanel(Fixture.qr()) { _ ->
            checkboxes()[Check.TRANSPARENT].assertIsOff()
            onNodeWithText("BACKGROUND").assertExists()
        }

    @Test
    fun `ticking transparency stores the flag and takes the background colour away`() =
        sourcePanel(Fixture.qr()) { get ->
            toggleCheckbox(Check.TRANSPARENT)

            assertEquals(
                Fixture.qr().copy(transparentBackground = true), get(),
                "ticking the box may change only that flag",
            )
            checkboxes()[Check.TRANSPARENT].assertIsOn()
            assertEquals(0, countOf("BACKGROUND"), "a transparent code has no background to colour")
        }

    @Test
    fun `unticking transparency brings the background colour back`() {
        sourcePanel(Fixture.qr().copy(transparentBackground = true)) { get ->
            toggleCheckbox(Check.TRANSPARENT)

            assertEquals(false, (get() as SceneSource.QRCodeSource).transparentBackground)
            onNodeWithText("BACKGROUND").assertExists()
        }
    }

    // ── Error correction ──────────────────────────────────────────────────────

    @Test
    fun `the error correction dropdown offers every level`() = sourcePanel(Fixture.qr()) { _ ->
        openDropdown(showing = "M")

        listOf("L", "Q").forEach { level ->
            assertEquals(1, countOf(level), "level \"$level\" must be offered")
        }
        assertEquals(2, countOf("M"), "M is both the closed selector and a menu entry")
        assertEquals(2, countOf("H"), "H is offered, alongside the header's own Height caption")
    }

    @Test
    fun `choosing an error correction level stores it`() {
        listOf("L", "Q", "H").forEach { level ->
            sourcePanel(Fixture.qr()) { get ->
                chooseFromDropdown(showing = "M", option = level)

                assertEquals(
                    Fixture.qr().copy(errorCorrection = level), get(),
                    "choosing \"$level\" may write only the error correction level",
                )
            }
        }
    }
}
