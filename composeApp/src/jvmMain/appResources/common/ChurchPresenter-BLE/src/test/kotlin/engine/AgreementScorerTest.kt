package engine

import engine.engine.AgreementScorer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgreementScorerTest {

    @Test fun `identical text scores full overlap`() {
        assertEquals(1.0, AgreementScorer.score("for god so loved the world", "for god so loved the world", ""))
    }

    @Test fun `ё and е spellings cross-match`() {
        // Verse text often carries ё where STT writes е — folding makes them equal.
        val score = AgreementScorer.score("Твёрдого духом Ты хранишь в совершенном мире", "твердого духом ты хранишь в совершенном мире", "")
        assertEquals(1.0, score, "ё/е spellings should fold together, got $score")
    }

    @Test fun `accented latin words stay whole`() {
        // The old a-z letter class split "hätte"/"wäre" into fragments; \\p{L} keeps them whole,
        // so a German verse matches its own text at full score.
        val text = "wenn er uns nicht gerettet hätte wären wir verloren"
        assertEquals(1.0, AgreementScorer.score(text, text, ""))
        // And fragments of a broken tokenization must NOT count as matches for unrelated text.
        assertTrue(AgreementScorer.score("hätte wären", "tte ren", "") < 1.0)
    }

    @Test fun `coverage measures verse words found in track`() {
        assertTrue(AgreementScorer.coverage("for god so loved the world", "he said for god so loved the world today") >= 0.9)
        assertTrue(AgreementScorer.coverage("for god so loved the world", "completely unrelated speech") <= 0.1)
    }
}
