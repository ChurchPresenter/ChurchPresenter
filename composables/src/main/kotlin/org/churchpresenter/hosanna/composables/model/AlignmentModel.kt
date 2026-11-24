package org.churchpresenter.hosanna.composables.model

/**
 * Where text sits inside its box: [Left], [Center], [Right] and [Justify] across, [Top], [Middle] and [Bottom] down.
 * Used by [org.churchpresenter.hosanna.composables.button.TextAlignmentButtons].
 */
enum class TextAlignment { Left, Center, Right, Justify, Top, Middle, Bottom }

/**
 * An edge or center a layer is lined up to: [Left], [Center] and [Right] across, [Top], [Middle] and [Bottom] down.
 * Used by [org.churchpresenter.hosanna.composables.button.LayerAlignmentButtons].
 */
enum class LayerAlignment { Left, Center, Right, Top, Middle, Bottom }

internal val HorizontalTextAlignments =
    listOf(TextAlignment.Left, TextAlignment.Center, TextAlignment.Right, TextAlignment.Justify)

internal val VerticalTextAlignments = listOf(TextAlignment.Top, TextAlignment.Middle, TextAlignment.Bottom)

internal val HorizontalLayerAlignments = listOf(LayerAlignment.Left, LayerAlignment.Center, LayerAlignment.Right)

internal val VerticalLayerAlignments = listOf(LayerAlignment.Top, LayerAlignment.Middle, LayerAlignment.Bottom)
