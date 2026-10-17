package org.churchpresenter.buildlogic

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule
import javax.inject.Inject

/**
 * Lifts what a library asks for below a security floor to that floor, in its published metadata.
 *
 * A metadata rule rather than `resolutionStrategy.eachDependency`: it reaches every resolution in
 * the project, detached configurations included, and `configurations.configureEach` never sees
 * those — pitest builds its own copy of the test classpath as one. Only an older request is lifted;
 * a newer one is left alone. Guava keeps the flavour (`-android`, `-jre`) it was asked for.
 *
 * [floors] maps `group:name` to the floor's version.
 */
abstract class SecurityFloorRule @Inject constructor(
    private val floors: Map<String, String>,
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                forEach { dependency ->
                    val floor = floors[dependency.module.toString()] ?: return@forEach
                    val asked = dependency.versionConstraint.requiredVersion
                        .ifEmpty { dependency.versionConstraint.preferredVersion }
                    if (asked.isNotEmpty() && olderThan(asked, floor)) {
                        val flavour =
                            if (dependency.group == "com.google.guava") "-" + asked.substringAfter('-', "jre") else ""
                        dependency.version { require(floor + flavour) }
                        dependency.because("a security advisory fixed at $floor")
                    }
                }
            }
        }
    }
}

/** Whether dotted version [a] is older than [b], comparing the leading numbers of each part. */
fun olderThan(a: String, b: String): Boolean {
    val left = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val right = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrElse(i) { 0 }
        val r = right.getOrElse(i) { 0 }
        if (l != r) return l < r
    }
    return false
}
