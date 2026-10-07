# `:helper` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**Wick**, the helper lamp in the main window's corner: a tip of the day, display setup, typed
requests ("make the song background blue", "show John 3:16", "where do I add a song?") and the
spotlight that rings real controls. The lamp is drawn in Compose (`LampMascot`), not an asset.

A real Gradle module of this build: `include(":helper")`, `implementation(projects.helper)`. Its only
consumer is `:composeApp`. It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`,
`:theme`, `:calendar` (for `parseReference`) and `:diagnostics`, and nothing of `:composeApp`'s.

## Seams to the app

- **`HelperActionExecutor`** is how anything gets done. The helper decides what to do and asks first;
  the app (`HelperWiring.kt`, `AppHelperExecutor`) carries it out and returns an `ActionOutcome` —
  done with an optional `UndoEntry`, refused with a reason, or a tour.
- **`HelperInputs`** is what the app hands the overlay each frame: the helper's own settings and
  their writer, the suggestions, the screens, a `ResolveContext` and whether anything is live. Plain
  values and callbacks; the helper never holds a view model.
- **The spotlight** is split: tagging (`Modifier.guideTarget`, `GuideTargets`, `GuideSession`,
  `GuideTargetRegistry`) is in `:shared-ui`'s `guide` package, because `:songs` tags a control too;
  drawing (`GuideSpotlightHost`) is here. Every window that holds tagged controls wraps its content in
  its own host — the main window and the Settings dialog do.
- **Persisted state** is `HelperSettings` in `:settings` (`AppSettings.helper`). Suggestion ids in
  `SuggestionIds` are stored there — never rename one.

## Layout

| Package | Owns |
|---|---|
| root | `HelperState` (the conversation, tours, undo), `HelperText`, `HelperActionExecutor` |
| `action/` | `HelperAction` (everything it can do), confirmation text, settings edits, undo |
| `intent/` | `IntentResolver`, `RuleIntentResolver`, the vocabulary, the "where is" topics |
| `suggest/` | `HelperSignals`, `suggestionsFor`, the tips |
| `display/` | `HelperScreen` and the display-setup flow |
| `ui/` | The lamp, the overlay and bubble, the replies, display setup, the spotlight host |

## Rules

- **Dev mode only, for now.** The app draws the lamp, the Help → Show Helper item and the System
  tab's Helper card only when `AppRootState.isDevMode` — the Developer menu's own gate. Lift it there
  when Wick ships.
- **Everything that changes something is confirmed.** `HelperAction.needsConfirmation` is false only
  for pointing at things, opening a window and showing a key.
- **Quiet during a service.** No suggestion, no badge, no animation while anything is live; the
  bubble never opens by itself.
- **A resolver may only answer with a `HelperAction`.** A model-backed resolver added later
  implements `IntentResolver` and is bound by the same list and the same confirmation.
- **The vocabulary is English only.** `Vocabulary` and `ColorNames` read what the operator types;
  they move into one table per language when another is added. Every line the helper *says* is a
  string resource.
- **Text style lives on profiles.** `withFontStep` edits every profile in use, never the global
  song/Bible settings, which `SettingsManager` strips of styling on save.
- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.

## Commands

```bash
./gradlew :helper:test :helper:detekt
./gradlew :helper:jacocoTestCoverageVerification
```
