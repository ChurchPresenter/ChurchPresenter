package org.churchpresenter.helper.intent.semantic

/** The bundled model, loaded once for the whole suite: reading 23 MB per test class would be most of its time. */
internal object TestModel {
    val encoder: MiniLmEncoder by lazy { MiniLmEncoder.load() }

    /** A resolver over the bundled catalog that never loads the model a second time. */
    fun resolver(): SemanticIntentResolver =
        SemanticIntentResolver(org.churchpresenter.helper.intent.RuleIntentResolver(), SemanticMatcher({ encoder }))
}
