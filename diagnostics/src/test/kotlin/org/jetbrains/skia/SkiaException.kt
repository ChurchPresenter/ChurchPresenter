package org.jetbrains.skia

/**
 * The `org.jetbrains.skia` half of the same stand-in — see
 * `org.jetbrains.skiko.RenderException` in this source set for why these exist.
 *
 * Both prefixes are classified as renderer faults, and a failure inside the Skia bindings
 * themselves is the case this one stands for.
 */
class SkiaException(message: String) : RuntimeException(message)
