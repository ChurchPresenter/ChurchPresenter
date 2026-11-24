package org.churchpresenter.hosanna.composables.model

internal fun moveTab(keys: List<String>, key: String, by: Int): List<String> {
    val from = keys.indexOf(key)
    val to = from + by
    if (from < 0 || to !in keys.indices) return keys
    return keys.toMutableList().apply { add(to, removeAt(from)) }
}

internal fun canHideTab(key: String, keys: List<String>, hidden: Set<String>): Boolean =
    key in hidden || keys.count { it !in hidden } > 1
