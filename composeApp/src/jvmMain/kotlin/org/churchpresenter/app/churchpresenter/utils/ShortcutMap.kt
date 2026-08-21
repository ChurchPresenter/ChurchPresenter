package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.models.ShortcutScope

/**
 * The bindings actually in force — defaults with the user's overrides applied.
 *
 * Every key handler in the app asks this rather than comparing a `Key` literal, and every key label
 * shown in the UI is rendered from it, so the two cannot drift.
 *
 * Resolution is done once at construction, not per key press: a service can generate a lot of key
 * events and this sits on the path of all of them.
 */
class ShortcutMap internal constructor(
    private val bindings: Map<ShortcutAction, List<KeyChord>>,
) {
    fun chordsFor(action: ShortcutAction): List<KeyChord> = bindings[action].orEmpty()

    fun matches(action: ShortcutAction, event: KeyEvent): Boolean =
        chordsFor(action).any { it.matches(event) }

    /**
     * The action [event] triggers within [scope], or null.
     *
     * Only the action's own scope is considered — a caller in a tab handler asks for its tab, the
     * root handler asks for [ShortcutScope.GLOBAL]. Overlap is a question for [conflictFor], not
     * for dispatch, because each handler physically only runs in one place.
     */
    fun actionFor(event: KeyEvent, scope: ShortcutScope): ShortcutAction? =
        ShortcutAction.entries.firstOrNull { it.scope == scope && matches(it, event) }

    /**
     * The action already using [chord] that would stop [action] working, or null when it is free.
     *
     * Scope overlap is what makes it a conflict — see `ShortcutScope.overlaps`. Binding `Space` in
     * Media when Pictures already uses it is fine and always has been.
     */
    fun conflictFor(chord: KeyChord, action: ShortcutAction): ShortcutAction? =
        ShortcutAction.entries.firstOrNull { other ->
            other != action &&
                other.scope.overlaps(action.scope) &&
                chordsFor(other).any { it == chord }
        }

    /**
     * Every action that shares a binding with another it competes with, and what it collides with.
     *
     * The same question [conflictFor] answers for one chord, asked of the whole registry at once —
     * the shortcuts dialog needs the full picture to count conflicts, mark the categories holding
     * one, and name the clash under a row, and doing that through the pairwise call would walk the
     * registry once per action.
     *
     * An unbound action never appears: two actions with no chords share nothing. Entries are
     * symmetric, so a colliding pair is listed under both halves.
     */
    fun conflicts(): Map<ShortcutAction, List<ShortcutAction>> {
        val sharing = mutableMapOf<KeyChord, MutableList<ShortcutAction>>()
        ShortcutAction.entries.forEach { action ->
            chordsFor(action).forEach { chord -> sharing.getOrPut(chord) { mutableListOf() } += action }
        }
        val found = mutableMapOf<ShortcutAction, MutableSet<ShortcutAction>>()
        sharing.values.filter { it.size > 1 }.forEach { actions ->
            actions.forEach { action ->
                // A set, because two actions bound to the same *two* chords would otherwise name
                // each other twice — Previous Slide and Next Slide each carry an arrow pair.
                val clashes = actions.filter { it != action && it.scope.overlaps(action.scope) }
                if (clashes.isNotEmpty()) found.getOrPut(action) { linkedSetOf() } += clashes
            }
        }
        return found.mapValues { (_, clashes) -> clashes.toList() }
    }

    /** True when the user has moved this action off the binding it ships with. */
    fun isCustomized(action: ShortcutAction): Boolean = chordsFor(action) != action.defaults

    companion object {
        val DEFAULT: ShortcutMap = ShortcutMap(ShortcutAction.entries.associateWith { it.defaults })

        fun from(settings: KeyboardShortcutSettings): ShortcutMap = ShortcutMap(
            ShortcutAction.entries.associateWith { action ->
                // Absent key → the default. Present-but-empty → deliberately unbound. An override
                // naming an action this build no longer has simply never gets looked up.
                settings.overrides[action.name] ?: action.defaults
            }
        )
    }
}

/**
 * The bindings in force for the composition beneath it.
 *
 * A `CompositionLocal` rather than a parameter threaded through every tab: the key handlers that
 * need it sit deep in seven tab composables whose signatures are already long, and existing tests
 * that compose a tab keep working against [ShortcutMap.DEFAULT] with no change. It holds no
 * mutable state and is not a ViewModel, so the ViewModel-ownership rule in AGENT.md does not apply.
 */
val LocalShortcuts = staticCompositionLocalOf { ShortcutMap.DEFAULT }
