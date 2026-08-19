package presentation.engine.model

import java.io.File

/**
 * Builds a [Deck] backed by a PDF file, for tests that need a deck with particular slides without
 * parsing a real one.
 *
 * [Deck]'s constructor and [DeckSource] are `internal` on purpose — only the loaders in this module
 * build a deck, and how it reproduces pixels is not part of the public contract. This fixture is
 * the sanctioned way past that for consumers' tests, which is why it lives in `testFixtures` and
 * not in `main`: take `testFixtures(projects.presentationEngine)` to reach it.
 */
fun pdfDeck(
    sourceFile: File,
    slides: List<Slide>,
    slideWidthPt: Double = 720.0,
    slideHeightPt: Double = 405.0,
    warnings: List<String> = emptyList(),
): Deck = Deck(
    sourceFile = sourceFile,
    format = DeckFormat.PDF,
    slideWidthPt = slideWidthPt,
    slideHeightPt = slideHeightPt,
    slides = slides,
    warnings = warnings,
    source = DeckSource.Pdf(sourceFile),
)
