# `:calendar` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Calendar Manager**: every planned service, on a month grid, each with a run of show — opened
from the Help menu beside the Song Library Manager. A real Gradle module of this build —
`include(":calendar")`, `implementation(projects.calendar)`.

It takes `:core-models` and `:theme` and nothing else of the app's.

## The one decision the rest of this module rests on

**A run of show is a `List<ScheduleItem>` and nothing else.**

There is no parallel row type. A section heading is a `ScheduleItem.LabelItem` with the colors it
already has; a planned service is the same shape as a saved `.schedule` file plus a start time.
The consequences are the reason it is written that way:

- "Load into Schedule" is a **copy**, not a conversion, so nothing can be lost in a mapping.
- **Automation is a property of the row.** `RowTiming` (`:core-models`) says when a row starts on
  its own, how long it runs, how many times, and what happens at the end — kept *beside* the list
  in `PlannedService.timing`, keyed by row id like `plannedSeconds`, so a song stays a song. The
  picker doubles as the row editor (click a row): pick a replacement, or change the timing panel.
  It goes into the Schedule with the run of show (`timingForSchedule()`) and fires *from* the
  Schedule: the engine (`CueRunner`) watches the live schedule, not `calendar.json`.
- **A cue is a row too** — `ScheduleItem.CueItem`, for a standalone action (blank the outputs, go
  live) with nothing of its own to show. On the calendar its time is relative to the service start;
  `rowsForSchedule()` pins it to the clock as it is loaded.
- A cue's payload is an ordinary `ScheduleItem`, which the app's own `executeProjectItem` already
  knows how to put on screen — the planner never has to learn what any item type *means*.
- An existing `.schedule` file could be dropped onto a calendar day for free.
- `PlannedService.cues` / `SavedTemplate.cues` are the **old** shape, read only: `withCuesAsRows()`
  folds them into `items` on load and nothing writes them again.

**Do not add a `CalendarItem`/`RunRow` type.** If a row needs something a `ScheduleItem` has no
field for, it belongs beside the list (as `plannedSeconds` does — a map keyed by item id), not in a
new type wrapping it.

## Package

**`org.churchpresenter.calendar`** (`.model` for the plain logic, `.ui` for the screens). The
Compose Resources class is generated into `org.churchpresenter.calendar.generated.resources`, set by
`packageOfResClass` in `build.gradle.kts` and **not** by any source file. Move one without the other
and the module stops compiling with unresolved `Res` references that no source file explains.

## Layout

| File | Holds |
|---|---|
| `model/CalendarModels.kt` | `CalendarDocument`, `PlannedService`, `ServiceKind`, `SectionStyle` — the file format; `ServiceCue` is the pre-row cue shape, read only |
| `model/CalendarTime.kt` | Dates, the locale week start, month names, the grid's dates. Pure |
| `model/DurationText.kt` | `4:30` ⇄ 270 seconds. Pure |
| `model/ClockText.kt` | `18:30` ⇄ `6:30 PM`: the clock format, and what a time field accepts. Pure |
| `model/RunClock.kt`, `model/RowClock.kt` | Each row's projected clock time, and whether it is exact |
| `model/CueTiming.kt` | When a cue fires, where a cue row goes in the list, what an action can point at. Pure |
| `model/CueEngine.kt` | Which cue rows are due now — the engine's whole decision. Pure |
| `model/ServiceMove.kt` | Moving a service: every pinned row and cue travels with its start time. Pure |
| `model/AutoLoad.kt` | When a service is due to be loaded: its first row's clock time, less five minutes. Pure |
| `model/CueStatus.kt` | Each cue's fired / next status against a clock, the rows and timing as they go into the Schedule, the old-file migration. Pure |
| `model/TimerRows.kt` | Reading and setting a timer row's length. Timers come in as presets. Pure |
| `model/ReferenceParser.kt` | `John 3:16-17` → a `BibleVerseItem`; and the browsed-verse builder |
| `model/RowIdentity.kt` | Re-keying rows, and the unique-id pass every load goes through |
| `model/ServiceTemplate.kt` | What a new service starts from |
| `model/RunOfShowPdf.kt` | The PDF export, via PDFBox |
| `CalendarStore.kt` | `calendar.json`, its three backups and its corrupt-file quarantine |
| `CalendarState.kt` | What the window is showing; the only thing that writes the store |
| `CalendarHost.kt` | The whole surface between this module and the app |
| `CueRunner.kt` | The automation engine over the live schedule, `fireCue` (the one place a cue becomes host calls) and `CueFeed`, what has fired |
| `ServiceAutoLoader.kt` | Loads the service that is about to start into the Schedule tab, with the window closed |
| `ui/Sheet.kt` | The dialog scaffold and the parts every dialog is built from |
| `ui/Fields.kt` | `CompactTextField`, `FieldLabel` and the `commitOnExit` modifier |
| `ui/Metrics.kt` | The design's sizes and type, named once — `CalendarMetrics` for the panes, `SheetMetrics` for the dialogs |
| `ui/ClockFormat.kt` | `LocalUse24HourClock`, the one place the clock format is read from |
| `ui/ReorderState.kt` | Drag-to-reorder for the run of show, by row key |
| `ui/RunClockState.kt` | The clock the run of show judges its cues against: wall clock today, stepped preview otherwise |
| `ui/CueRows.kt` | A cue row of the run of show — armed, skipped or fired there; there is no cue editor |
| `ui/TimingPanel.kt`, `ui/TimingDraft.kt` | The row editor's Starts / Runs / Repeats / At end panel, and its typed state |
| `ui/CueToast.kt` | The `Cue fired` card in the window's corner |
| `ui/` (the rest) | The screens and the three dialogs |
| `sync/RelayWire.kt` | The wire contract with the relay: `RemoteRow`/`RemoteService` (the sealed plaintext), `SealedRecord`, the request/response shapes, `WireLimits`. the relay's `SYNC.md`, kept with the relay in the website repository, is the spec |
| `sync/Envelope.kt`, `sync/Sealing.kt` | AES-GCM sealing of what the relay stores |
| `sync/Projection.kt` | Desktop → wire: a run of show reduced to what a phone may see |
| `sync/Resolver.kt`, `sync/Sanitize.kt` | Wire → desktop: rows rebuilt against the library, presets and the local copy; strings cleaned and capped |
| `sync/RelayClient.kt` | The HTTPS calls, over a `RelayTransport` taken as a parameter |
| `sync/SyncCoordinator.kt` | One round: pull → open → resolve → `mergedWith` → save → push authoritative |
| `sync/CatalogSync.kt` | The songbooks kept on the relay for the phones (`catalog:<songbook>` records, `CatalogRecord`), pushed only when a book's bytes changed; `catalog-sync.json` remembers what was pushed |

## Rules

- **Never resolve the storage folder here.** `CalendarStore` takes it as a parameter and the app
  passes `AppDataDir.resolve()`. That is what keeps this module free of a `:settings` dependency and
  what lets the store be tested against a temp directory.
- **No palette of its own.** Row icons and colors come from `:theme`'s `MaterialTheme.semantic`
  content roles (`contentSongs`, `contentBible`, …), so a song is the same color here as everywhere
  else in the app and follows all nine themes. The only stored hexes are `ServiceKind`'s three, and
  those are *category* colors, the same kind of thing a schedule label stores.
- **No month or weekday strings.** `java.time`'s `getDisplayName` has them, correctly cased and
  abbreviated, in every locale — see `CalendarTime.monthName`. The CCLI report window's
  `ccli_month_*` (12 strings × 34 locales) is what not doing this costs.
- **The week does not start on Monday.** It starts on `WeekFields.of(locale).firstDayOfWeek`.
- **Never draw a stored time as it is stored.** `startTime` and `absoluteTime` are `HH:mm` on disk
  whatever the user chose; every place one is shown goes through `clockText(…, LocalUse24HourClock.current)`
  and every field one is typed into parses with `parseClockText`, which takes both forms. The format
  is `CalendarPreferences.use24HourClock`, provided once at the root of `CalendarApp`.
- **Icons are vector assets, never text or emoji.** The design this was built from uses `♪`, `✝`
  and `▣`; the root `AGENT.md` forbids exactly that. See `ui/ItemLook.kt`.
- **Every mutation saves.** `CalendarState.commit` is the only place the document changes and the
  only place it is written, so "saved immediately" is true by construction rather than by every
  author remembering. Do not add a mutation that bypasses it.
- **Never use Material 3's `OutlinedTextField` here.** It is ~56dp tall with a floating label and
  its own padding; in these dense panes that makes a one-line entry taller than the chip row beside
  it and turns every dialog into a form. `ui/Fields.kt`'s `CompactTextField` is what the design
  specifies — roughly 30dp, one border, the label above it as separate text.
- **Every dialog goes through `SheetScaffold`.** The three of them have to agree on the header, the
  tab strip, the row card and the footer rule; building each out of whatever M3 offered is exactly
  what made them drift apart the first time.
- **An in-place edit commits on exit, never per keystroke** — `Modifier.commitOnExit`. Two traps it
  exists for: `onFocusChanged` fires once with `isFocused=false` as a field *attaches*, which closes
  an editor the instant it opens if acted on; and every commit rewrites `calendar.json`, so writing
  per character writes the file a dozen times for one duration.
- **Run-of-show list keys are the row id alone, never the index.** Drag-to-reorder needs a key that
  follows the row. Uniqueness is guaranteed at the source by `CalendarDocument.withUniqueRowIds()`
  on load, not assumed — a file on disk cannot promise it. The song picker is the exception and
  keys by file path: `songId` is `songbook::number`, and a real library repeats a number (this
  developer's has six such collisions), which a `LazyColumn` rejects outright.
- **The PDF export must embed a font.** PDFBox's built-in Helvetica is WinAnsi-encoded and *throws*
  on the first Cyrillic character, so for a Russian song library an un-embedded export is a crash,
  not a degraded one. The face comes from the app through `CalendarHost.pdfFont` rather than being
  bundled twice.

## Sync

- Rows from the relay are rebuilt by `Resolver`; nothing on the wire is deserialized into a
  `ScheduleItem` directly, and a `ref` resolves only against the local copy of the same service.
- `Sealing` takes an `Envelope`; the key comes from `:settings` through the app, never from here.
- `RelayClient` is the only network code in this module.

## `calendar.json`

Its own file in `AppDataDir.resolve()`, deliberately separate from `settings.json`: it is the one
the user asked to be able to lose without losing anything else.

- Written through `writeTextAtomically`, so a reader — or a sync daemon — cannot catch it half done.
- Three backups behind it (`calendar.json.bak1`…`.bak3`), rotated **before** each write, so a backup
  is always of content that was complete.
- A parse failure is never fatal. The unreadable file is moved to `calendar.json.corrupt-<stamp>`
  rather than deleted, the newest readable backup is opened instead, and `CalendarSource` says which
  — which the window then shows as a banner. **Keep that banner.** A planner that silently opens a
  week-old copy is indistinguishable from one that opened the current file, and the user finds out
  by not seeing their services.

## Commands

```bash
./gradlew :calendar:compileKotlin   # fast compile check
./gradlew :calendar:test            # its suite
./gradlew :calendar:detekt          # its gate — run it LAST before you stop
```

## Coverage

No `extra["coverageFloors"]` and no `extra["coverageExcludes"]`: the root build's 85% on all six
counters applies. `model/` is pure and carries the logic worth pinning — the duration and reference
parsers, the month grid, the document's service list — and `CalendarStore`'s backup and recovery
paths are reachable against a temp directory without composing anything.
