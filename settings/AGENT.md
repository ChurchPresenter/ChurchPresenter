# `:settings` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**Everything the app persists**: the settings data classes, the `SettingsManager` that loads,
migrates and saves `settings.json`, and the `Constants` those defaults are spelled with. A real
Gradle module of this build: `include(":settings")`, `implementation(projects.settings)`.

It keeps the packages it always had — `…churchpresenter.data.settings`, `…churchpresenter.data`
and `…churchpresenter.utils` — so **no import in the app changed when it moved**, the same way
`:core-models` kept `…churchpresenter.models`. Two modules sharing a package name is fine on a
plain classpath; do not "tidy" this into a new package, because that is 700 import lines of churn
for no gain.

## Backward compatibility — the whole point of the package rule

`settings.json` is **unaffected by where these classes live**, and it must stay that way. Nothing
here is a sealed hierarchy, nothing carries `@SerialName`, and no `Json` instance sets a
`classDiscriminator`, so a fully-qualified class name never reaches the file: kotlinx.serialization
writes property names and enum entry names only. That is what makes the module boundary free.

It stops being free the moment someone adds polymorphic serialization here. If a settings type ever
needs a sealed hierarchy, give every subtype an explicit `@SerialName` that is **not** its FQCN, or
the next move of this module silently orphans every user's configuration.

`SettingsManager.CURRENT_SETTINGS_VERSION` and the raw-JSON migration chain live here too, so an
old document is still migrated on load and on Settings → Import.

## What lives here

| Path | Owns |
|---|---|
| `data/settings/*.kt` | The 34 settings data classes — one file per area (`AppSettings`, `BibleSettings`, `ScreenAssignment`, …) |
| `data/SettingsManager.kt` | Load, migrate, save, import, export; the versioned migration steps |
| `utils/Constants.kt` | `object Constants` — the string values settings defaults are written with, plus the fixed ports and wire header names |
| `utils/AppDataDir.kt` | Where the app persists: `~/.churchpresenter`, with the platform app-data folder behind it |
| `utils/UpdateCheckInterval.kt` | The startup-check interval enum, stored in `AppSettings` |
| `utils/ClockFormat.kt` | `isSystemUsing24HourFormat()` — asked by `AnnouncementsSettings.liveClockFormat`'s default |

**`ObsSceneSelection.kt` deliberately stayed in `:composeApp`.** It is a helper function over
`OBSSettings`, not persisted state, and it is the only thing in the package that needed
`presenter.Presenting` — moving it would have dragged the live-content enum down here.

The screen-device and aspect-ratio helpers that used to share `Constants.kt` are `utils/DisplayGeometry.kt`
and `utils/AspectRatio.kt` in `:composeApp`: they are `@Composable`, they read
`GraphicsEnvironment`, and they take a `ScreenAssignment` — i.e. they depend on this module, not
the other way round.

## Rules

- **Anything `:composeApp` calls has to be public here.** `internal` no longer reaches the app —
  that is what the `StoryPromptState` extensions found out. Keep genuinely module-private helpers
  `internal` (`usedEveryWeek`, `storyPromptWeekOf`, the `STORY_PROMPT_*` constants).
- **No Compose runtime, no composables, no Compose compiler plugin.** The `compose.ui` dependency
  exists only so `KeyChord`'s signature resolves where `KeyboardShortcutSettings` names it.
- **Nothing here may read the network, open a window or ask for a display.** File I/O is
  `SettingsManager` and `AppDataDir`, and that is the whole of it.
- A settings *default* may call into this module and nothing else. `AnnouncementsSettings` asks
  `isSystemUsing24HourFormat()`; that function lives here for exactly that reason.

## Commands

```bash
./gradlew :settings:test
./gradlew :settings:detekt                            # gate — no baseline, must be clean
./gradlew :settings:jacocoTestCoverageVerification
```

All three run in CI, gated on this directory or the shared build files changing.

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**, main and test both in scope.
  The three findings that came over with the code are `@Suppress`ed at the declaration with a
  reason, the way `:converter` and `:songlibrary` do it — do not add a baseline file here.
- **Coverage**: the root build's default six counters at 85%, all of them — **no**
  `coverageFloors`, **no** `coverageExcludes`. The tightest is METHOD, because every data class
  contributes generated `copy`/`componentN` accessors nothing calls; a new settings class with many
  fields and no test moves that number before it moves any other.

## Dependencies

`:core-models` (`KeyChord`, `SongTuning`, `CompanionSurfacePlacement`, `TimerModes`), `compose.ui`
for `KeyChord` alone, and `kotlinx-serialization-json`. Nothing of the app's own, ever.
