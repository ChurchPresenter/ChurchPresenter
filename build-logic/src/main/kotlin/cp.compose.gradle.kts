import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension

// A Compose module. Its strings and icons come from :strings and :icons, so a module with no
// `src/main/composeResources` of its own generates no `Res` class; one that has resources names its
// own package in `compose.resources { }`.
apply(plugin = "org.jetbrains.compose")
apply(plugin = "org.jetbrains.kotlin.plugin.compose")

if (!file("src/main/composeResources").exists()) {
    extensions.getByType<ComposeExtension>().extensions.configure<ResourcesExtension> {
        generateResClass = ResourcesExtension.ResourceClassGeneration.Never
    }
}
