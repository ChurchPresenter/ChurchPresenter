plugins {
    id("cp.kotlin-jvm")
    id("cp.compose")
}

compose.resources {
    packageOfResClass = "org.churchpresenter.icons.generated.resources"
    publicResClass = true
}

dependencies {
    implementation(libs.compose.runtime)
    api(libs.compose.components.resources)
}
