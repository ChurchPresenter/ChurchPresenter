plugins {
    alias(libs.plugins.kotlinJvm)
    `java-test-fixtures`
    alias(libs.plugins.detekt)
    jacoco
}

group = "org.churchpresenter"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The whole native surface, for the reasons `:ndi` gives: JNA works on JDK 21 and on arm64, and
    // `libomt` exports a flat C API of plain functions and one struct.
    implementation(libs.jna)

    // CrashReporter only. Not `api`: no OMT signature mentions a diagnostics type.
    implementation(projects.diagnostics)

    testImplementation(kotlin("test"))
    // FakeOmtLibrary is a fixture rather than a test class because :composeApp's own OMT tests need
    // it too — the same reason :ndi publishes FakeNdiLibrary this way.
    testImplementation(testFixtures(project(":omt")))
}

// Opt-in gate for OmtHardwareTest, which binds the real libomt and puts a source on the network: it
// loads a native library, advertises a sender other machines can discover and starts the library's
// own discovery threads, so it must never run as part of an ordinary `check`. Off by default; enable
// for a deliberate pass with:
//   ./gradlew :omt:test -PomtHardware=true -PomtLibrary=/dir/holding/libomt --tests '*OmtHardwareTest*'
tasks.withType<Test>().configureEach {
    systemProperty(
        "churchpresenter.omtHardware",
        if (project.hasProperty("omtHardware")) project.property("omtHardware").toString() else "false"
    )
    // The directory holding libomt and libvmx. Blank means the app's own bundled copy for this
    // platform, which is where `:composeApp:fetchBundledOmt` writes it.
    systemProperty(
        "churchpresenter.omtLibrary",
        if (project.hasProperty("omtLibrary")) project.property("omtLibrary").toString() else ""
    )
    // The source OmtLiveReceiverProbe receives — a name matched against discovery, or omt://host:port.
    systemProperty(
        "churchpresenter.omtSource",
        if (project.hasProperty("omtSource")) project.property("omtSource").toString() else ""
    )
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    // main + test only, as every other module of this build has it. NOT testFixtures: detekt's
    // default excludes cover test source sets by name and `testFixtures` is not among them.
    source.setFrom("src/main/kotlin", "src/test/kotlin")
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
