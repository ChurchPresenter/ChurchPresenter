package lottiegen.lottie.styles

import lottiegen.lottie.LottieBuilder
import lottiegen.model.LottieGenConfig

/**
 * Interface for animation style generators.
 * Each style creates layers in the LottieBuilder based on the resolved config.
 *
 * @param builder The LottieBuilder with duration already set
 * @param cfg The config with baseSize already scaled by (canvasH / 1080) * scaleFactor
 */
interface StyleGenerator {
    fun generate(builder: LottieBuilder, cfg: LottieGenConfig)
}
