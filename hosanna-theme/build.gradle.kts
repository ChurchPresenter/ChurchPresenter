plugins {
    id("cp.jvm-module")
    id("cp.compose")
}

extra["coverageFloors"] = mapOf("BRANCH" to "0.65")

compose.resources {
    packageOfResClass = "org.churchpresenter.hosanna.generated.resources"
}

dependencies {
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.components.resources)

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.compose.uiTest)
}

tasks.test {
    systemProperty("java.awt.headless", "true")
}
