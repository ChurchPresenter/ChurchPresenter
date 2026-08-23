# `:dictionary-settings-tab` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**One page of the options dialog** — the one that styles what the audience sees of a Strong's entry:
the word, its definition, its reference and its KJV usage, each with a colour, a font, a size, style
toggles and an optional shadow, plus the card background and the fade transitions.

`include(":dictionary-settings-tab")`, `implementation(projects.dictionarySettingsTab)`.

**It does not depend on `:dictionary`.** It reads and writes `DictionarySettings`, which lives in
`:settings`, and draws it with the settings fields from `:ui-components`. Nothing here knows what a
Strong's number is — which is why it is a module of its own rather than part of `:dictionary-tab`,
and why the split cost nothing: `:dictionary-tab` kept `api(projects.dictionary)` and this did not
need it.

## What `:composeApp` uses from it

One symbol: `DictionarySettingsTab`, drawn by `OptionsDialog`. It takes an `AppSettings` and an
`((AppSettings) -> AppSettings) -> Unit`, and nothing else — no view model, no presenter, no app
type. **Keep it that way.**

## Layout

`src/main/kotlin/org/churchpresenter/dictionary/settings/` — a sibling package of
`:dictionary-tab`'s `org.churchpresenter.dictionary.tab`.

One production file, `DictionarySettingsTab.kt`, and eight test classes beside it.

## Rules

- **No `:dictionary`, no `:bible`, no view model.** The moment this needs one, it has stopped being
  a settings page and belongs back with the tab.
- **The screenshots are COMMITTED**, under `dictionary-settings-tab/screenshots/`, and the section
  they are written to is still `dictionarySettingsTab` — the same path relative to the module's
  screenshot root that they had in `:composeApp`, so the images are the ones already reviewed. The
  root `AGENT.md` rule applies in full: never move them under `build/`.
- **The harness is `dictionaryTab`**, as it always was. It briefly became `dictionarySettingsTab`
  while these files shared a package with `DictionaryTab`'s own `dictionaryTab` harness; the
  collision went away with the split and the original name came back.

## Gates

**No `coverageFloors` override, and it needs none.** Measured 2026-08-23:

| counter | measured |
|---|---|
| INSTRUCTION | 1.000 |
| LINE | 1.000 |
| CLASS | 1.000 |
| METHOD | 1.000 |
| COMPLEXITY | 1.000 |
| BRANCH | 1.000 |

**Read that honestly before quoting it.** The source file contains **zero** `if`, `when`, `&&`,
`||` or elvis of its own — it is a flat declarative tree of sections, rows and fields. So 100% here
means "every line of a branchless file is executed", not "this is the best-tested module in the
build". It is a fair result for what the file is; do not read more into it.

> Before the `updateDict` refactor below, JaCoCo emitted **no BRANCH counter at all** for this
> module: after its Kotlin filters there was nothing branch-shaped left to measure. The one lambda
> that refactor introduced is what put the counter back. Whether a counter appears here is a
> property of the codegen, not of how well the module is tested — do not read a missing counter as
> a gap, or a restored one as progress.

## Commands

```bash
./gradlew :dictionary-settings-tab:test      # 8 test classes, 84 tests
./gradlew :dictionary-settings-tab:detekt    # 31 baselined entries, nothing else
./gradlew :dictionary-settings-tab:jacocoTestCoverageVerification
./gradlew :dictionary-settings-tab:verifyRoborazziJvm --tests '*ScreenshotTest*'   # 12 images
./gradlew :dictionary-settings-tab:recordRoborazziJvm --tests '*ScreenshotTest*'
```

**The detekt baseline holds one entry** — the `LongMethod` on `DictionarySettingsTab` itself,
carried across verbatim from `:composeApp`'s baseline as the file moved.

The 30 `MaxLineLength` entries that came with it are **gone, fixed at source rather than
suppressed**. All thirty were this file restating
`onSettingsChange { s -> s.copy(dictionarySettings = …) }` at thirty call sites; one local
`updateDict` lambda at the top of the composable made every one of them short:

```kotlin
val updateDict: (DictionarySettings.() -> DictionarySettings) -> Unit = { change ->
    onSettingsChange { s -> s.copy(dictionarySettings = s.dictionarySettings.change()) }
}
// then, at each control:
onColorChange = { updateDict { copy(wordColor = it) } }
```

**Keep using it.** A new control that spells the two nested copies out again is both a
`MaxLineLength` finding and the thing that made thirty of them. Never add a second baseline entry.
