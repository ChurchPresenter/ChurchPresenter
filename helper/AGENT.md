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
- **Send this chat** goes out only through `HelperInputs.onSendChat`, which the app wires to Contact Us
  (`WickChatSender.kt`, type `wickChat`). The card previews the chat already masked by `report/Redact.kt`;
  nothing is sent until its Send. A sent chat is never committed or used as test data.
- **Use is counted** through `HelperState`'s `onUsed`, called when Wick carries something out (a request,
  a tour, a shortcut shown, display setup); the app records `UsageEvent.WICK_USED` once per run.

## Layout

| Package | Owns |
|---|---|
| root | `HelperState` (the conversation, tours, undo), `HelperText`, `HelperActionExecutor` |
| `action/` | `HelperAction` (everything it can do), confirmation text, settings edits, undo |
| `intent/` | `IntentResolver`, `RuleIntentResolver`, the vocabulary, the "where is" topics |
| `intent/semantic/` | The sentence model (`MiniLmEncoder`, `WordPieceTokenizer`), its catalog (`WickCatalog`), `SemanticMatcher` and `SemanticIntentResolver` |
| `suggest/` | `HelperSignals`, `suggestionsFor`, the tips |
| `display/` | `HelperScreen` and the display-setup flow |
| `ui/` | The lamp, the overlay and bubble, the replies, display setup, the spotlight host |
| `report/` | Masking a chat before it is previewed or sent (`Redact.kt`), and how a send went |
| `pack/` | Wick packs: `wick-pack/pack.json` read, checked against this build, fetched and cached |

## Rules

- **Off until the operator starts it.** Help → Show Helper is always in the menu; it sets
  `HelperSettings.startedByUser`, and only then (or in dev mode) does the app draw the lamp, play the
  intro, offer tips and show the System tab's Helper card (`AppRootState.wickAvailable`). Nothing of
  Wick appears on its own in production.
- **Everything that changes something is confirmed.** `HelperAction.needsConfirmation` is false only
  for pointing at things, opening a window and showing a key.
- **Quiet during a service.** No suggestion, no badge, no animation while anything is live; the
  bubble never opens by itself.
- **A resolver may only answer with a `HelperAction`.** `SemanticIntentResolver` is bound by the same
  list and the same confirmation as the rules.

## The sentence model

What the rules miss is read by **all-MiniLM-L6-v2**, run in plain Kotlin (`MiniLmEncoder`, no native
library): ~30 ms on one lowest-priority thread (`wick-model`), about 23 MB while loaded, loaded on the
first miss and let go after ten idle minutes. It never runs while anything is live (the overlay
already holds still) and never acts on a weak match: at `ACT` or above it answers with that action,
otherwise it offers the closest chips — the model's score nudged by `keywordScores`, which catches
typos. Without the model, the helper is its rules alone.

- **One model file, replaced in place.** `helper/tools/export_minilm.py` writes `wick/minilm-l6.bin`
  (int8, ~23 MB), `wick/vocab.txt` and the parity fixtures from a pinned revision; re-running
  overwrites them. Never keep a second model beside it. `THIRD_PARTY_MINILM.md` is its licence notice.
- **The catalog is generated from the codebase, and committed.** `wick/catalog.tsv` holds every chip,
  every multi-word `*Topics.kt` phrase the rules understand, every `guideTarget(…)` control's own labels,
  every Settings page's labels, every tab and every shortcut, each with its vector. Never edit it by hand:
  `./gradlew :helper:updateWickCatalog` regenerates it, re-embedding only what changed, and
  `WickCatalogTest` fails until it has been run after a change it covers. Which files make up each
  Settings page is `WickCatalogSource.SETTINGS_FILES`.
- **Measure before changing a threshold.** `./gradlew :helper:wickEval` runs `wick/understanding.tsv`
  (rewordings, typos, off-topic requests) and fails on any wrong action by the model or when fewer than
  90% of the requests it can see are reached. It prints which the rules got wrong — theirs to fix.
- **The rules read English; every other language is a glossary.** `Vocabulary`, `ColorNames` and the
  rules stay English. `intent/glossary/` holds one `Glossary` per shipped locale that rewrites a typed
  request into those English words; `Glossaries.readings` tries the app's language, then the text as
  typed, then every other language. A new word goes in the glossary, never in a rule; a glossary
  never maps a Bible book name. Every line the helper *says* is a string resource.
- **Text style lives on profiles.** `withFontStep` edits every profile in use, never the global
  song/Bible settings, which `SettingsManager` strips of styling on save.
- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.

## Wick packs

What Wick can learn without an app release: phrases that lead to things it already does, tours over
controls the app already tags, and tips (`wick-pack/README.md`). The app fetches `pack.json` from `main`
at most once a day, only once Wick is in use, and falls back to the cache, then to its bundled data.

- **Data only.** A pack can point and say things; it cannot change a setting, go live or run code.
  `parseWickPack` drops anything naming a target, a place or a tour this build does not have, and ignores
  a pack too big, malformed, or for a newer app (`minApp`, written as the app writes its version: `26.15.0`).
- **Edit `wick-pack/source.json`, never `pack.json`**, then run `./gradlew :helper:buildWickPack`.
  `WickPackSyncTest` fails until it has been run after a change.
- **English only**: hints and tips are shown as written; tips only in an English app.
- A dev build reads a local pack with `-Dchurchpresenter.wickPackUrl=<path or file: URL>`.

## Commands

```bash
./gradlew :helper:test :helper:detekt
./gradlew :helper:recordRoborazziJvm --tests '*ScreenshotTest*'   # images in helper/screenshots/
./gradlew :helper:verifyRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :helper:jacocoTestCoverageVerification
./gradlew :helper:wickEval            # how well Wick understands rewordings
./gradlew :helper:updateWickCatalog   # after tagging a control, rewording a label or adding a topic phrase
./gradlew :helper:buildWickPack       # after editing wick-pack/source.json
python3 helper/tools/export_minilm.py # re-export the model (needs numpy and tokenizers)
```
