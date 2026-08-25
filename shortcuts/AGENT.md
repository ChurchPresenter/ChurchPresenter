# `:shortcuts` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The keyboard shortcuts**: what actions there are, what each is currently bound to, and how a
binding is written out for a human to read.

`include(":shortcuts")`, `implementation(projects.shortcuts)`.

Three files:

| file | holds |
|---|---|
| `ShortcutAction.kt` | `ShortcutAction` — every action, its scope, its default chords and its description — and `ShortcutScope` |
| `ShortcutMap.kt` | `ShortcutMap` (defaults with the user's overrides applied, plus matching and conflict detection) and the `LocalShortcuts` composition local |
| `ShortcutLabels.kt` | rendering a `KeyChord` for a human: the label, the individual keycaps, and the searchable text |

## Why it is its own module

**Every tab needs it.** Six are still in `:composeApp` — Bible, Songs, Pictures, Presentation,
Media and Canvas — and each one that moves out would otherwise have to reach back into the app for
`LocalShortcuts`. It was extracted ahead of the Canvas tab for exactly that reason.

**It is not part of `:ui-components`**, and must not be moved there. `ShortcutMap` reads
`KeyboardShortcutSettings`, and the root `AGENT.md` records a deliberate decision that
`:ui-components` does not gain a production `:settings` dependency — the same rule that keeps
`assignedDisplayBounds` in `:composeApp`.

## What moved with it, and what did not

- **`Tabs` went to `:core-models`** (`org.churchpresenter.core.models.tabs`). `ShortcutAction` names
  one as its `targetTab` — the F-keys that jump to a tab — and this module cannot depend on the app.
  It is a plain enum with no dependencies, so `:core-models` is its natural home.
- **The shortcuts *dialog* stayed in `:composeApp`.** `KeyboardShortcutsDialog`,
  `ShortcutBindingRow` and `ShortcutCategoryRail` are options-dialog pages, peers of the other
  settings screens. This module is the model and the rendering, not the editor.

## A package that did not match its directory

`:core-models`' `KeyEventFixture.kt` sits at `.../core/utils/` but declared
`package org.churchpresenter.app.churchpresenter.utils`. That is why `ShortcutMapTest` used to
resolve `keyDown()` with no import at all — it was in the same package by accident. Moving the test
here broke it, and the fix was to make the package match the directory
(`org.churchpresenter.core.utils`) and give all four callers an explicit import. If a shared fixture
ever resolves without an import again, check for the same thing.

## Testing

Most of `ShortcutLabels.kt` is `@Composable` — it reads translated strings — so its tests go through
`runComposeUiTest` and pull the result out of the composition. `ShortcutLabelRenderingTest.composed`
is the helper; follow it rather than inventing another.

**An empty override is how "unbound" is expressed.** `ShortcutMap.from` falls back to
`action.defaults` when an action is absent from `overrides`, so a test that wants an unbound action
must map its name to `emptyList()` — leaving the key out gives the shipped binding instead, which is
the opposite.

No `extra["coverageFloors"]` and no `extra["coverageExcludes"]` — the module clears the root build's
0.85 default on all six counters:

| counter | value |
|---|---|
| INSTRUCTION | 0.959 |
| BRANCH | 0.925 |
| LINE | 0.981 |
| COMPLEXITY | 0.936 |
| METHOD | 1.000 |
| CLASS | 1.000 |

There is no display, no network and no device anywhere in here — it is a table and some string
formatting. **If a number here drops, something has been added that does not belong.**

## Commands

```bash
./gradlew :shortcuts:test
./gradlew :shortcuts:detekt
./gradlew :shortcuts:jacocoTestCoverageVerification
```
