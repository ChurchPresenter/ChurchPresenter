package org.churchpresenter.app.churchpresenter.data

/**
 * A language the interface is offered in.
 *
 * [code] is both the BCP 47 tag the app sets as the JVM default locale and the suffix of the
 * `composeResources/values-<code>` folder the strings come from — `LanguageTest` holds the two
 * together, since nothing in the code names those folders.
 *
 * [rightToLeft] drives the layout direction the whole control surface is composed in; see
 * `LanguageProvider`.
 */
enum class Language(val code: String, val nativeName: String, val rightToLeft: Boolean = false) {
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English"),
    UKRAINIAN("uk", "Українська"),
    KAZAKH("kk", "Қазақ"),
    GERMAN("de", "Deutsch"),
    POLISH("pl", "Polski"),
    BELARUSIAN("be", "Беларуская"),
    CZECH("cs", "Čeština"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    DUTCH("nl", "Nederlands"),
    PORTUGUESE("pt", "Português"),
    ROMANIAN("ro", "Română"),
    SLOVAK("sk", "Slovenčina"),
    ESTONIAN("et", "Eesti"),
    LATVIAN("lv", "Latviešu"),
    CROATIAN("hr", "Hrvatski"),
    SWEDISH("sv", "Svenska"),
    NORWEGIAN("no", "Norsk bokmål"),
    FINNISH("fi", "Suomi"),
    TURKISH("tr", "Türkçe"),
    UZBEK("uz", "Oʻzbekcha"),
    ARABIC("ar", "العربية", rightToLeft = true),
    PERSIAN("fa", "فارسی", rightToLeft = true),
    HINDI("hi", "हिन्दी"),
    NEPALI("ne", "नेपाली"),
    THAI("th", "ไทย"),
    LAO("lo", "ລາວ"),
    JAPANESE("ja", "日本語"),
    CHINESE("zh", "简体中文"),
    INDONESIAN("id", "Bahasa Indonesia"),
    MALAY("ms", "Bahasa Melayu"),
    TAMIL("ta", "தமிழ்"),
    TAGALOG("tl", "Tagalog"),
    SWAHILI("sw", "Kiswahili")
}
