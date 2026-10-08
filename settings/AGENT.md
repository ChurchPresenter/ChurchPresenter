# `:settings` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**Everything the app persists**: the settings data classes, the `SettingsManager` that loads,
migrates and saves `settings.json`, and the `Constants` those defaults are spelled with. A real
Gradle module of this build: `include(":settings")`, `implementation(projects.settings)`.

**The packages are `org.churchpresenter.settings` and `org.churchpresenter.settings.utils`.** The
module held three of the app's own packages — `…churchpresenter.data.settings`,
`…churchpresenter.data` and `…churchpresenter.utils` — which cost nothing at extraction time and
one real thing afterwards: all three are **still live in `:composeApp`**, so a settings class
resolved with no import in any app file that happened to share its package, and the dependency was
invisible there.

`settings/` holds the persisted classes and `SettingsManager`; `settings/utils/` holds `Constants`,
`AppDataDir`, `ClockFormat` and `UpdateCheckInterval` — the constants and helpers those defaults
are spelled with, which are not themselves settings.

**Never rewrite by package prefix.** `:composeApp` owns 34 files in `…utils` and 34 in `…data`;
a prefix rewrite drags all of them along. Key on the
symbols this module declares. In particular `:shared-ui` has **its own `utils/Constants.kt`**
holding top-level functions like `presenterScreenBounds()` — the `SceneViewModel` suites stub its
file class by name, `mockkStatic("org.churchpresenter.sharedui.utils.ConstantsKt")`, and that
string is not this module's and must not be re-pointed at it.

**`settings.json` is unaffected by any of this.** Nothing here is a sealed `@Serializable`, so no
class name is ever written to disk — the file is keyed on property names and enum constant names,
and stays readable across the rename in both directions.

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
| `*.kt` (package root) | The settings data classes and their helpers — one file per area (`AppSettings`, `BibleSettings`, `ScreenAssignment`, `TextBox`, …) |
| `SettingsManager.kt` | Load, migrate, save, import, export, and the registry of migration steps in version order |
| `SettingsJson.kt` | The one `Json` configuration settings are read and written with, and `parseSettingsRoot` |
| `Settings*Migrations.kt` | The steps themselves, by what they reshape: `Styling`, `Profile`, `Screen`, `Output`, and `Decoded` (the ones on the decoded object) |
| `SettingsProfileStripping.kt` | What a save takes out of the document because the output profiles own it |
| `SettingsRepair.kt`, `SettingsBackups.kt` | The repairs every load applies; the backups kept before a rewrite and of an unreadable file |
| `utils/Constants.kt` | `object Constants` — the string values settings defaults are written with, plus the fixed ports and wire header names |
| `utils/AppDataDir.kt` | Where the app persists: `~/.churchpresenter`, with the platform app-data folder behind it |
| `utils/UpdateCheckInterval.kt` | The startup-check interval enum, stored in `AppSettings` |
| `utils/ClockFormat.kt` | `isSystemUsing24HourFormat()` — asked by `AnnouncementsSettings.liveClockFormat`'s default |

**`ObsSceneSelection.kt` is not here.** It is a helper over `OBSSettings`, not persisted state, and
it needs `Presenting`, so it lives in `:obs` with the rest of the OBS integration.

**Only `object Constants` came out of `Constants.kt`.** The screen-device, aspect-ratio and song
header helpers that shared that file stayed behind — now in `:shared-ui`'s `utils/Constants.kt`,
`AspectRatio.kt` and `SongLines.kt`: they are `@Composable`, they read `GraphicsEnvironment`, and they
take a `ScreenAssignment` — i.e. they depend on this module, not the other way round. Two files
named `Constants.kt` is fine here for the same reason two modules may share a package: this one
declares nothing at top level, so it generates no `ConstantsKt` facade to collide with that one.

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

## Output profiles

An output renders with `AppSettings.resolvedFor(profile)`: the profile's copy of each styled
category, with the fields that are **one per install** taken from the document instead. Those are
named in `BIBLE_GLOBAL_KEYS`, `SONG_GLOBAL_KEYS`, `STT_GLOBAL_KEYS` and `QA_GLOBAL_KEYS`
(`OutputSettingsResolution.kt`).

- **A new field on one of those categories is per-profile by default.** If it is a folder, a
  file list, a server address or anything else that must not differ between screens, add it to the
  category's keep-list in the same change — otherwise one profile can quietly point somewhere else.
- **Moving styling onto the profile needs a migration step** that seeds every profile from the
  document's current value, only where the profile has none of its own (see versions 14 and 15).
  Without it every profile silently takes the class defaults on the next load.
- **Linked profiles store every value in full** (`LinkedProfiles.kt`); `OutputProfile.overrides`
  names a follower's own values as paths into its serialized settings, and everything else is
  rewritten from its master on every edit and on load. **A new `OutputProfile` field is inherited
  by followers by default** -- if it describes the profile itself rather than how it draws (a name,
  the preview's shape), add it to `IDENTITY_KEYS` in `LinkedProfilePaths.kt` in the same change.
- **A Bible translation's look follows the stack's All layer** (`BibleAllLayer.kt`) except at the
  fields named in its `ownStyleKeys`. Presenters read each translation's own values and never the
  layer.
- **A song's All layer is stored only where the first language has values of its own**
  (`SongLayoutExtras.allLanguages`): everywhere else All *is* the first language's look, so settings
  written before it read unchanged. New song settings go in `SongLayoutExtras`, never on `SongSettings`
  itself, which is at the JVM's constructor-parameter ceiling.
- **"Different from defaults" compares with `defaultBaseline()`** (`ProfileDefaults.kt`): the profile a
  new one would be. A field that is working state rather than a setting -- like the All layers and
  `ownStyleKeys` -- belongs in its `BOOKKEEPING_PATHS`, or every profile lists it as a change.
  `BackgroundConfig.ownBackgroundType` -- the type a surface remembers while it follows the
  profile's default -- is one of them.
- **Text boxes are per profile** (`TextBox.kt`): every category that draws text carries
  `textBoxes`, a map from `textBoxKey(item, lowerThird, language)` -- `"LYRICS#1@LT"` -- to a
  `TextBox` in percent of its area, and `textBoxOptions` for the page. Songs keep theirs on
  `SongLayoutExtras`; Bible on `BibleSettings` itself, keyed by translation file name. A box that is
  turned off keeps its rectangle, so a key's presence says nothing about whether it draws -- read
  `boxAt(key).enabled`. A new item that gets a box names its key in a constant beside the others.
- **Preview layouts are one per install**, on `ProjectionSettings` (`previewLayouts`,
  `activePreviewLayout`, `previewLayoutFillsPanel`, `listUnplacedOutputs`), not on a profile: they
  arrange the operator's own panel, not an output. The tree operations are in `PreviewLayouts.kt`
  and never mutate -- each returns the new root.
- **Version 19** moves the four per-translation Bible offsets onto boxes (`migrateBibleOffsetsToBoxes`),
  and **version 20** turns preview groups into one layout (`migratePreviewGroupsToLayout`), leaving
  outputs no group held out of the panel as they were.
- **Version 21** drops the document's copies of the caption, Q&A, dictionary and subtitle looks
  (`migrateTopLevelStylingOut`), and `saveSettings` keeps them dropped
  (`stripProfileOwnedStyling`). The document's `sttSettings`/`qaSettings` save only their
  `*_GLOBAL_KEYS`, and its `dictionarySettings`/`mediaSettings` are not saved at all. Read those
  looks from a profile, never from the document.
- **Version 22** does the same for Songs and the Bible (`migrateBibleAndSongsOut`): the document
  keeps `SONG_GLOBAL_KEYS`, `BIBLE_GLOBAL_KEYS` and, for each translation in the stack, only
  `BIBLE_TRANSLATION_GLOBAL_KEYS`. First it gives every profile what it lacked: a whole section,
  or a translation its Bible settings never styled. A document with no profiles gets its factory
  profile built from its own look.
- **Version 23** turns a profile's fifteen flat `show*` switches into its `look`, grouped by layer
  (`OutputLook.kt`, `migrateShowSwitchesIntoLooks`), and renames a follower's overrides of them by
  `LEGACY_LOOK_PATHS`. Edit a look through `withLook`/`withBackground`/`withMedia`/`withSlide`.
- **The main window follows a profile** for everything that is not one per install
  (`operatorProfile`: the first profile an output uses, else the first profile). Read
  `operatorSongSettings()`/`operatorBibleSettings()` for verse splitting, the title slide, chorus
  repeat, line mode and the rest. `appSettings.songSettings`/`bibleSettings` hold only the
  install-wide keys; every other field there is a class default.

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
