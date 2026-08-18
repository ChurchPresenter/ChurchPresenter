import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
            exclude("ui/**", "MainKt*")
        }
    )
}

// The converter also ships as a standalone desktop app (.github/workflows/converter-installers.yml
// packages it), separately from the copy the main app opens from its Help menu.
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
