import io.github.takahirom.roborazzi.RoborazziExtension

// Roborazzi screenshots, committed beside the module that shoots them -- never under build/, where
// `clean` wipes them and no reviewer can open them (see AGENT.md, Screenshots).
apply(plugin = "io.github.takahirom.roborazzi")

extensions.configure<RoborazziExtension> {
    outputDir.set(layout.projectDirectory.dir("screenshots"))
}
