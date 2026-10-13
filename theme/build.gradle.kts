plugins {
    id("cp.jvm-module")
    id("cp.compose")
}

dependencies {
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.compose.uiTest)
}

tasks.test {
    systemProperty("java.awt.headless", "true")
}
