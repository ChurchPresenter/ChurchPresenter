# `:statistics` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

What was presented and when, and the report a church files with CCLI. It holds:
- `StatisticsManager`, the all-time counters (`statistics.json`) and the timestamped play log
  (`play_log.json`), both under `~/.churchpresenter`;
- the queries over the log (`StatisticsQueries.kt`): top songs and verses, activity over time, the
  CCLI number lookup against the song catalog, and the CSV and XLS exports;
- `StatisticsPeriod`, the quick ranges (last 3/6/12 months, all time, a year);
- `LiveDurationLog`, how long each schedule row has stayed on screen, at the path its caller passes;
- `CCLIReportDialog` (Help → statistics) and the report it draws: `CCLIReportContent`, its range
  header and library filter (`CCLIReportRange.kt`), the song and verse tables
  (`CCLIReportTables.kt`) and the activity chart (`CCLIReportActivity.kt`).

The report window lives here with the data because it reads the period arithmetic
(`ROLLING_MONTHS`, `availableYears`, `resolveDates`) that is internal to this module.

A real Gradle module of this build: `include(":statistics")`, `implementation(projects.statistics)`.
`:composeApp` is its only consumer: `AppRootState` owns the manager, `MainWindowDialogs` opens the
report, the go-live wiring records plays (`recordSongWentLive` stays in the app because it also
sends usage telemetry), the remote projection reads it, and the duration log (`asDurationRow`)
times the schedule rows the Calendar Manager plans with.

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings` and `:theme`, plus Apache
POI's core jar for the XLS export (HSSF, no OOXML schema jar), and nothing of `:composeApp`'s.

## Seams to the app

- `StatisticsManager` implements neither `:songs`' `SongPlayCounts` nor `:bible-tab`'s
  `BibleVerseStatistics` — that would pull both tab modules in. `MainTabArea.kt` adapts it with
  the two SAM constructors over `getSongPlayCount` and `recordVerseDisplay`.

## Package

**`org.churchpresenter.statistics`**. The test helpers (`withStatsHome`, `playSong`/`playVerse`, the
summary fixtures, `CcliLabel` and the report finders) are in `src/testFixtures`. They are public, so
the app's tests can use them.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
  `CCLIReportContent` is public only for the app's preview screenshot suite.
- **Tests live here**, beside the code. The report's screenshots stay in the app
  (`AppPreviewStatisticsScreenshotTest`), where they were recorded.
- **The files stay where they are.** `statistics.json` and `play_log.json` are read from existing
  installs; renaming or moving either loses a church's play history.
- `LiveDurationLog` is not synchronized, unlike `StatisticsManager`'s one lock. It moved here as it
  was; a caller on a second thread needs that settled first.

## Commands

```bash
./gradlew :statistics:test :statistics:detekt
./gradlew :statistics:jacocoTestCoverageVerification
```
