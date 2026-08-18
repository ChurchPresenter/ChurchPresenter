import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    // Compose artefacts come from the version catalogue rather than the `compose.*` accessors, so
    // this module and :composeApp resolve the SAME material3 and icon versions -- composeApp
    // depends on this module, and a second version line here would silently upgrade the app's.
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.sqlite.jdbc)
    // FreeShow and MediaShout libraries are JSON. Declared here because this module compiles on
    // its own now -- it used to borrow the app's copy through the kotlin.srcDir mount.
    implementation(libs.kotlinx.serialization.json)
    implementation("org.apache.pdfbox:pdfbox:2.0.33")
    // poi-ooxml-lite is swapped for poi-ooxml-full, matching :composeApp and the Presentation
    // Engine: exactly ONE schema jar may be on the classpath, and this module's jar is on the
    // app's, so a lite that arrived here transitively would put both there.
    implementation("org.apache.poi:poi-ooxml:5.3.0") {
        exclude(group = "org.apache.poi", module = "poi-ooxml-lite")
    }
    implementation("org.apache.poi:poi-ooxml-full:5.3.0")

    testImplementation(kotlin("test"))
}

// Same rules as the app's gate, on this module's own sources. It carries no baseline: the app's
// baseline.xml is a snapshot of jvmMain findings from the day the size rules were switched on, and
// nothing in it applies here -- so this module simply has to come out clean.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    // `ui/**` is left out, and that is the one carve-out here. It is 4,100 lines of Compose
    // desktop written before this gate existed -- App.kt alone is 2,818 -- and it accounts for 208
    // findings against 126 in the code below it, almost all MaxLineLength on composable calls.
    // That is the same shape as the Compose UI :composeApp keeps in config/detekt/baseline.xml
    // rather than gating, and this module has no baseline. Everything that parses a file is
    // analysed, and is clean.
    source.setFrom("src/main/kotlin/converter", "src/test/kotlin")
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Coverage for this module's own logic. The UI package is excluded: it is Compose desktop
// composables that need a real display, and the app's own report has the same carve-out.
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            // MainKt is the standalone entry point; Compose emits a synthetic ComposableSingletons
            // holder beside it, excluded for the same reason the app's report excludes its own.
            exclude("ui/**", "MainKt*", "ComposableSingletons*")
        }
    )
}

// The converter also ships as a standalone desktop app (.github/workflows/converter-installers.yml
// packages it), separately from the copy the main app opens from its Help menu.
// The floors the module's own suite has to keep clearing. Same six counters and the same rule as
// :composeApp uses -- each floor is the highest multiple of 5 the current number clears, so it pins
// the level against regression rather than demanding new work. Measured 2026-08-17:
//
//   counter       measured   floor   margin
//   INSTRUCTION     95.86%     95%     +0.9
//   BRANCH          83.13%     80%     +3.1
//   LINE            98.03%     95%     +3.0
//   COMPLEXITY      77.12%     75%     +2.1
//   METHOD          97.00%     95%     +2.0
//   CLASS           98.85%     95%     +3.9
//
// Same carve-out as the report above: `ui/**` needs a display, and MainKt is the standalone entry
// point. What is left is parsing, which is testable in full and is where a regression would hurt.
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    executionData.setFrom(layout.buildDirectory.file("jacoco/test.exec"))
    sourceDirectories.setFrom(files("src/main/kotlin"))
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude("ui/**", "MainKt*", "ComposableSingletons*")
        }
    )
    violationRules {
        rule {
            limit { counter = "INSTRUCTION"; value = "COVEREDRATIO"; minimum = "0.95".toBigDecimal() }
            limit { counter = "BRANCH"; value = "COVEREDRATIO"; minimum = "0.80".toBigDecimal() }
            limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.95".toBigDecimal() }
            limit { counter = "COMPLEXITY"; value = "COVEREDRATIO"; minimum = "0.75".toBigDecimal() }
            limit { counter = "METHOD"; value = "COVEREDRATIO"; minimum = "0.95".toBigDecimal() }
            limit { counter = "CLASS"; value = "COVEREDRATIO"; minimum = "0.95".toBigDecimal() }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ChurchPresenter-Converter"
            packageVersion = "1.0.0"

            windows {
                menuGroup = "ChurchPresenter"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
        }
    }
}
