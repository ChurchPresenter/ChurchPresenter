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
