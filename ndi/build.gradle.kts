plugins {
    id("cp.jvm-module")
    `java-test-fixtures`
}

dependencies {
    // The whole native surface. Chosen over Devolay (unmaintained, no Apple Silicon native, and it
    // hardcodes a libndi.so.5 soname NDI 6 no longer ships) and over Panama/FFM (JDK 22; this build
    // targets 21). JNA works on 21 and on arm64, and the send API is six functions.
    implementation(libs.jna)

    // CrashReporter only. Not `api`: no NDI signature mentions a diagnostics type.
    implementation(projects.diagnostics)

    testImplementation(kotlin("test"))
    // FakeNdiLibrary is a fixture rather than a test class because :composeApp's own NDI tests need
    // it too — the same reason :atem publishes FakeAtemSwitcher this way.
    testImplementation(testFixtures(project(":ndi")))
}

// Opt-in gate for NdiHardwareTest, which binds the real NDI Runtime and puts a source on the
// network: it loads a native library, advertises a sender that other machines can discover, and
// pays runtime-init time, so it must never run as part of an ordinary `check`. Off by default;
// enable for a deliberate hardware pass with:
//   ./gradlew :ndi:test -PndiHardware=true --tests '*NdiHardwareTest*'
tasks.withType<Test>().configureEach {
    systemProperty(
        "churchpresenter.ndiHardware",
        if (project.hasProperty("ndiHardware")) project.property("ndiHardware").toString() else "false"
    )
    // How long NdiLiveSenderProbe holds a source up for a human to look at. Only read by that probe.
    systemProperty(
        "churchpresenter.ndiSeconds",
        if (project.hasProperty("ndiSeconds")) project.property("ndiSeconds").toString() else "30"
    )
}

detekt {
    // main + test only, as every other module of this build has it. NOT testFixtures: detekt's
    // default excludes cover test source sets by name and `testFixtures` is not among them.
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

