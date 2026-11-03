package org.churchpresenter.liveoutput

/**
 * The text on air, as one value: the slide's content, the song's line or section, the verses, the
 * question and the dictionary entry. It changes the moment the next one goes live.
 */
private fun PresenterManager.onAirText(): Any = listOf(
    slideContent.value,
    displayedLyricSection.value,
    displayedSongPosition.value,
    displayedVerses.value,
    displayedQuestion.value,
    displayedDictionaryEntry.value,
)

/**
 * Takes the words on air off every output and leaves the background up -- the pause between verses
 * while the band plays on. A song's or Bible's words go and their background stays; a question or
 * a dictionary entry goes with the card it is drawn on, which is the text's own. A Q&A QR code is
 * not text and stays. Only the text on air now is cleared: the next line, verse, question or entry
 * put live shows as usual.
 */
fun PresenterManager.clearTextOnly() {
    textClearedFor.value = onAirText()
}

/** Puts back the words [clearTextOnly] took off, without waiting for the next line. */
fun PresenterManager.restoreText() {
    textClearedFor.value = null
}

/** Whether the words on air are cleared, the background left up -- see [clearTextOnly]. */
val PresenterManager.textCleared: Boolean
    get() = textClearedFor.value?.let { it == onAirText() } ?: false

/** Whether a question's card is off for [clearTextOnly]; its QR code is not text and stays up. */
internal fun PresenterManager.hidesQuestion(): Boolean = textCleared && !showQRCodeOnDisplay.value
