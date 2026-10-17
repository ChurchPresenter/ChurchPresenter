package org.churchpresenter.buildlogic

/**
 * [depends] -- the value of a .deb's `Depends:` field -- with every package Debian renamed for the
 * 64-bit time_t transition (`libasound2t64`, `libglib2.0-0t64`, …) also accepting its old name.
 *
 * jpackage names what the .deb needs by the packages that hold those libraries on the machine that
 * built it, and CI builds on an Ubuntu that has made the transition. Ubuntu 22.04 and Debian 12 still
 * ship the old names, and a .deb that asks only for the new ones will not install there (issue #875).
 */
fun withPreT64Alternatives(depends: String): String =
    depends.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ") { dep ->
        val name = dep.substringBefore(' ')
        if ('|' in dep || !name.endsWith(T64) || name.length == T64.length) dep
        else "$dep | ${name.removeSuffix(T64)}${dep.removePrefix(name)}"
    }

private const val T64 = "t64"

/**
 * The `Recommends:` value of the .deb: the system libraries `libcef.so`, the web engine, links to.
 * JCEF is downloaded on first use, so jpackage cannot see these, and without them the Web tab
 * shows its missing-library message on a minimal install.
 *
 * Named as Ubuntu 24.04 has them, each renamed one with its 22.04 name as an alternative
 * ([withPreT64Alternatives]). apt skips a Recommends it cannot find without an error, so a name
 * must exist on both releases: a wrong one fails silently.
 */
val webEngineRecommends: String = withPreT64Alternatives(
    listOf(
        "libnss3", "libnspr4", "libcups2t64", "libgbm1", "libatk1.0-0t64", "libatk-bridge2.0-0t64",
        "libatspi2.0-0t64", "libxkbcommon0", "libxcomposite1", "libxdamage1", "libxrandr2", "libxfixes3",
        "libpango-1.0-0", "libcairo2", "libdbus-1-3", "libglib2.0-0t64", "libudev1",
    ).joinToString(", "),
)
