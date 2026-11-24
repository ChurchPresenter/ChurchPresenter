package org.churchpresenter.hosanna.composables.model

enum class TextAlignment { Left, Center, Right, Justify, Top, Middle, Bottom }

enum class LayerAlignment { Left, Center, Right, Top, Middle, Bottom }

internal val HorizontalTextAlignments =
    listOf(TextAlignment.Left, TextAlignment.Center, TextAlignment.Right, TextAlignment.Justify)

internal val VerticalTextAlignments = listOf(TextAlignment.Top, TextAlignment.Middle, TextAlignment.Bottom)

internal val HorizontalLayerAlignments = listOf(LayerAlignment.Left, LayerAlignment.Center, LayerAlignment.Right)

internal val VerticalLayerAlignments = listOf(LayerAlignment.Top, LayerAlignment.Middle, LayerAlignment.Bottom)
