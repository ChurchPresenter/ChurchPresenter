package org.churchpresenter.app.churchpresenter.data

import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.typeOf

/**
 * Forces Compose Multiplatform's string-resource loading to resolve against a fixed English/light
 * environment instead of asking AWT for the real display's DPI. That query — `Toolkit * .getScreenResolution()`, reached from `getSystemResourceEnvironment()` — throws
 * `HeadlessException` under this suite's `-Djava.awt.headless=true`, which is why
 * [BibleBookNames.getEnglishBookNames]/[BibleBookNames.getBookNameMapping] and
 * [BibleBookAbbreviations]'s loaders were previously untestable (see AGENT.md's note on Compose
 * string resources).
 *
 * The seam this uses — `org.jetbrains.compose.resources.getResourceEnvironment`, a top-level `var`
 * — is `internal` to the compose-resources library, so it cannot be named or assigned from plain
 * Kotlin source in this module; Kotlin's `internal` is enforced by the compiler from module
 * metadata, not by a JVM access flag, and reflection operates below that layer. Its own doc
 * comment reads "the function reference will be overridden for tests", so this is exactly the
 * mechanism its author built for this, just not exposed across module boundaries. The property's
 * declared type erases to `kotlin.reflect.KFunction` with a `checkcast` to
 * `kotlin.jvm.functions.Function0` at its one call site — verified by decompiling
 * `ResourceEnvironmentKt.getSystemResourceEnvironment()` — so the replacement below has to
 * genuinely implement both interfaces, not just satisfy a lambda shape.
 */
internal object ComposeResourceEnvironmentTestSupport {

    private val resourceEnvironmentKt = Class.forName("org.jetbrains.compose.resources.ResourceEnvironmentKt")
    private val kFunctionClass = Class.forName("kotlin.reflect.KFunction")
    private val getCurrent = resourceEnvironmentKt.getMethod("getGetResourceEnvironment")
    private val setCurrent = resourceEnvironmentKt.getMethod("setGetResourceEnvironment", kFunctionClass)

    private val fixedEnglishEnvironment: Any by lazy {
        val languageQualifier = Class.forName("org.jetbrains.compose.resources.LanguageQualifier")
        val regionQualifier = Class.forName("org.jetbrains.compose.resources.RegionQualifier")
        val themeQualifier = Class.forName("org.jetbrains.compose.resources.ThemeQualifier")
        val densityQualifier = Class.forName("org.jetbrains.compose.resources.DensityQualifier")
        val resourceEnvironment = Class.forName("org.jetbrains.compose.resources.ResourceEnvironment")

        val language = languageQualifier.getConstructor(String::class.java).newInstance("en")
        val region = regionQualifier.getConstructor(String::class.java).newInstance("")
        val theme = themeQualifier.getField("LIGHT").get(null)
        val density = densityQualifier.getField("MDPI").get(null)

        resourceEnvironment
            .getConstructor(languageQualifier, regionQualifier, themeQualifier, densityQualifier)
            .newInstance(language, region, theme, density)
    }

    /** A `KFunction0`-shaped object standing in for `::getSystemEnvironment`. */
    private object FixedEnvironmentFunction : Function0<Any?>, KFunction<Any?> {
        override fun invoke(): Any? = fixedEnglishEnvironment
        override val annotations: List<Annotation> = emptyList()
        override val name: String = "fixedEnglishEnvironment"
        override val parameters: List<KParameter> = emptyList()
        override val returnType: KType get() = typeOf<Any?>()
        override val typeParameters: List<KTypeParameter> = emptyList()
        override val visibility: KVisibility = KVisibility.PUBLIC
        override val isFinal: Boolean = true
        override val isOpen: Boolean = false
        override val isAbstract: Boolean = false
        override val isSuspend: Boolean = false
        override val isInline: Boolean = false
        override val isExternal: Boolean = false
        override val isOperator: Boolean = false
        override val isInfix: Boolean = false
        override fun call(vararg args: Any?): Any? = invoke()
        override fun callBy(args: Map<KParameter, Any?>): Any? = invoke()
    }

    /** Runs [block] with the fixed environment installed; always restores the real one after. */
    fun <T> withFixedEnvironment(block: () -> T): T {
        val original = getCurrent.invoke(null)
        setCurrent.invoke(null, FixedEnvironmentFunction)
        try {
            return block()
        } finally {
            setCurrent.invoke(null, original)
        }
    }
}
