package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.band.BandEntrance
import org.churchpresenter.lottiegen.band.BandTimeline
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.BibleLottieGenViewModel
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.Tokens

private const val MIN_SECONDS = 0.1f
private const val MAX_SECONDS = 4f
private const val MAX_HOLD_SECONDS = 10f
private const val MIN_TICKER_SPEED = 30f
private const val MAX_TICKER_SPEED = 600f
private const val MIN_PHASE_WEIGHT = 0.05f

/** The crossfade's colour on its slider: not a phase of the file, so not on the bar. */
private val CROSSFADE_COLOR = Color(0xFFE06C9F)

/** The five phases of a band, each in the colour it keeps across the timing bar, the sliders and the scrubber. */
internal enum class BandPhase(val color: Color) {
    BAND_IN(Color(0xFF5B9DF5)),
    TEXT_IN(Color(0xFF16B8A6)),
    HOLD(Color(0xFF6B7078)),
    TEXT_OUT(Color(0xFFC9A2F0)),
    BAND_OUT(Color(0xFFE8A33D)),
    ;

    val label: String
        get() = when (this) {
            BAND_IN -> Strings.bandTimeBandIn
            TEXT_IN -> Strings.bandTimeTextIn
            HOLD -> Strings.bandPhaseHold
            TEXT_OUT -> Strings.bandTimeTextOut
            BAND_OUT -> Strings.bandTimeBandOut
        }

    fun seconds(cfg: BibleLottieGenConfig): Float = when (this) {
        BAND_IN -> cfg.bgInSeconds
        TEXT_IN -> cfg.textInSeconds
        HOLD -> cfg.holdSeconds
        TEXT_OUT -> cfg.textOutSeconds
        BAND_OUT -> cfg.bgOutSeconds
    }

    fun with(cfg: BibleLottieGenConfig, seconds: Float): BibleLottieGenConfig = when (this) {
        BAND_IN -> cfg.copy(bgInSeconds = seconds)
        TEXT_IN -> cfg.copy(textInSeconds = seconds)
        HOLD -> cfg.copy(holdSeconds = seconds)
        TEXT_OUT -> cfg.copy(textOutSeconds = seconds)
        BAND_OUT -> cfg.copy(bgOutSeconds = seconds)
    }

    /** Where this phase starts, as a fraction of the whole. */
    fun startFraction(t: BandTimeline): Float = when (this) {
        BAND_IN -> 0f
        TEXT_IN -> t.textStart.toFloat() / t.totalFrames
        HOLD -> t.holdStart.toFloat() / t.totalFrames
        TEXT_OUT -> t.textOutStart.toFloat() / t.totalFrames
        BAND_OUT -> t.bgOutStart.toFloat() / t.totalFrames
    }

    companion object {
        /** The phase playing at [progress] of the whole. */
        fun at(t: BandTimeline, progress: Float): BandPhase =
            entries.lastOrNull { progress >= it.startFraction(t) } ?: BAND_IN
    }
}

/** The Motion pane: how the band and the text arrive, and how long each phase runs. */
@Composable
internal fun MotionSection(viewModel: BibleLottieGenViewModel) {
    val cfg = viewModel.config
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EnumDropdown(
            Strings.bandEntrance, cfg.entrance, BandEntrance.entries, { Strings.bandEnumLabel("entrance", it.name) },
            modifier = Modifier.weight(1f),
        ) { v -> viewModel.updateConfig { it.copy(entrance = v) } }
        EnumDropdown(
            Strings.bandTextAnimation, cfg.textAnimation, TextAnimation.entries,
            { Strings.bandEnumLabel("text", it.name) },
            modifier = Modifier.weight(1f),
        ) { v -> viewModel.updateConfig { it.copy(textAnimation = v) } }
    }
    if (cfg.textAnimation == TextAnimation.TICKER) {
        ThinSlider(
            label = Strings.bandTickerSpeed,
            value = cfg.tickerPxPerSecond.toFloat(),
            onValueChange = { v -> viewModel.updateConfig { it.copy(tickerPxPerSecond = v.toInt()) } },
            valueRange = MIN_TICKER_SPEED..MAX_TICKER_SPEED,
            format = { it.toInt().toString() },
            unit = Strings.bandUnitPx + "/" + Strings.bandUnitSeconds,
        )
    }
    Hairline()
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Caption(Strings.bandTiming, Modifier.weight(1f))
        Text(
            "%.1f".format(viewModel.timeline.totalSeconds) + Strings.bandUnitSeconds,
            fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Tokens.Accent,
        )
    }
    PhaseBar(cfg)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        BandPhase.entries.forEach { phase ->
            InlineSlider(
                label = phase.label,
                value = phase.seconds(cfg),
                onValueChange = { v -> viewModel.updateConfig { phase.with(it, v) } },
                valueRange = MIN_SECONDS..(if (phase == BandPhase.HOLD) MAX_HOLD_SECONDS else MAX_SECONDS),
                format = { "%.1f".format(it) },
                unit = Strings.bandUnitSeconds,
                labelWidth = 74.dp,
                swatch = phase.color,
            )
        }
        // Verse to verse: the player's own move, so it is not on the bar above and the preview
        // never shows it, but it is timed here with the rest.
        InlineSlider(
            label = Strings.bandTimeCrossfade,
            value = cfg.swapSeconds,
            onValueChange = { v -> viewModel.updateConfig { it.copy(swapSeconds = v) } },
            valueRange = MIN_SECONDS..MAX_SECONDS,
            format = { "%.1f".format(it) },
            unit = Strings.bandUnitSeconds,
            labelWidth = 74.dp,
            swatch = CROSSFADE_COLOR,
        )
    }
}

/** The five phases side by side, each as wide as it is long. */
@Composable
private fun PhaseBar(cfg: BibleLottieGenConfig) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(FIELD_SHAPE)
            .border(1.dp, Tokens.FieldBorder, FIELD_SHAPE),
    ) {
        BandPhase.entries.forEach { phase ->
            val seconds = phase.seconds(cfg)
            Box(
                Modifier.weight(seconds.coerceAtLeast(MIN_PHASE_WEIGHT)).fillMaxWidth().height(22.dp)
                    .background(phase.color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "%.1f".format(seconds), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Clip,
                    color = if (phase == BandPhase.HOLD) Color.White else Tokens.OnAccent,
                )
            }
        }
    }
}
