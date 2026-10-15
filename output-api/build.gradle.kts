import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    id("cp.jvm-module")
}

// The ports' default members compile to interface default methods only, without the DefaultImpls
// classes Kotlin adds for Java callers built in the old mode. Every caller here is Kotlin from this
// build; the bridges would be classes nothing can call.
kotlin {
    compilerOptions {
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    // The ports speak Compose state, a picture, the live-content enum, the models a tab puts on the
    // output, a deck and the embedded browser: all of it is in their signatures.
    api(libs.compose.runtime)
    api(libs.compose.ui)
    api(projects.sharedUi)
    api(projects.coreModels)
    api(projects.presentationEngine)
    api(libs.jcefmaven)

    testImplementation(kotlin("test"))
    // pdfDeck(): a Deck without parsing one, for the deck port's default arguments.
    testImplementation(testFixtures(projects.presentationEngine))
}
