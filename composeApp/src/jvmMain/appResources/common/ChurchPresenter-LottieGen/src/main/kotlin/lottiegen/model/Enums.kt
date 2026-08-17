package lottiegen.model

import lottiegen.ui.Strings

enum class AnimationStyle(val id: String, val labelKey: String) {
    BAR("1", "style_1"),
    BOXED("2", "style_2"),
    CIRCULAR("3", "style_3"),
    BANNER("4", "style_4"),
    GRADIENT_BAR("5", "style_5"),
    LINE_SPLIT("6", "style_6"),
    RANDOM_FADE("7", "style_7"),
    DIAGONAL("8", "style_8"),
    DIAGONAL_WIPE("9", "style_9"),
    DOUBLE_LINE("10", "style_10"),
    NEWS_TICKER("11", "style_11"),
    NEWS_BADGE("12", "style_12");

    val label: String get() = when (this) {
        BAR -> Strings.style1
        BOXED -> Strings.style2
        CIRCULAR -> Strings.style3
        BANNER -> Strings.style4
        GRADIENT_BAR -> Strings.style5
        LINE_SPLIT -> Strings.style6
        RANDOM_FADE -> Strings.style7
        DIAGONAL -> Strings.style8
        DIAGONAL_WIPE -> Strings.style9
        DOUBLE_LINE -> Strings.style10
        NEWS_TICKER -> Strings.style11
        NEWS_BADGE -> Strings.style12
    }

    companion object {
        fun fromId(id: String): AnimationStyle = entries.first { it.id == id }
    }
}

enum class LottieAlignment(val id: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    val label: String get() = when (this) {
        LEFT -> Strings.alignLeft
        CENTER -> Strings.alignCenter
        RIGHT -> Strings.alignRight
    }

    companion object {
        fun fromId(id: String): LottieAlignment = entries.first { it.id == id }
    }
}

enum class TextTransform(val id: String) {
    NONE("none"),
    UPPERCASE("uppercase");

    val label: String get() = when (this) {
        NONE -> Strings.none
        UPPERCASE -> Strings.uppercase
    }

    companion object {
        fun fromId(id: String): TextTransform = entries.first { it.id == id }
    }
}

enum class LottieFont(val familyName: String, val hasRegular: Boolean = true, val hasBold: Boolean = true) {
    VERDANA("Verdana"),
    OPEN_SANS("Open Sans"),
    POPPINS("Poppins"),
    RALEWAY("Raleway"),
    ANONYMOUS_PRO("Anonymous Pro"),
    PATUA_ONE("Patua One", hasBold = false),
    LORA("Lora"),
    ABRIL_FATFACE("Abril Fatface", hasBold = false),
    COOKIE("Cookie", hasBold = false),
    OLEO_SCRIPT("Oleo Script"),
    KALAM("Kalam"),
    FREDOKA_ONE("Fredoka One", hasBold = false);
}
