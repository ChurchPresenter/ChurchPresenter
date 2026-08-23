# `:statistics` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**Everything the app has ever put on the screen, counted.** Every song and verse that goes live is
recorded here, and this module owns what happens to that record afterwards: the all-time tally, the
timestamped play log behind it, the periods a report can cover, and the CSV and Excel exports a
CCLI licence report is filed from. A real Gradle module of this build: `include(":statistics")`,
`implementation(projects.statistics)`.

The window that *draws* all this — `dialogs/CCLIReportDialog.kt` — stays in `:composeApp`. This
module is the numbers; that is the screen.

**The models are not here.** Every data class this module reads and returns lives in
`:core-models`, under `models/statistics/`, one type per file — the records on disk, the computed
report rows, the identity keys and the reporting period. This module is the behaviour over them.

| File | Owns |
|---|---|
| `StatisticsManager.kt` | Every question asked of the two stores: `recordSongDisplay`/`recordVerseDisplay`, the range and top-N queries, `getActivityByPeriod`, `clearSong`/`clearVerse`/`clearStatistics`, and `exportCcliCsv`/`exportFilteredXls` — plus the `key()` extensions that derive a `SongKey`/`VerseKey` from either store, and the pure helpers around them. |
| `StatisticsPeriod.kt` | Turning a period into dates: `ROLLING_MONTHS` and `resolveDates`/`resolve`/`availableYears`, which need a caller-supplied `today` and the earliest event on record — which is why they are here and the `StatisticsPeriod` type is not. |

## Rules

- **This is the one part of the app whose output goes to a third party.** A licence report is filed
  against these numbers, so a song counted twice, missed, or attributed to the wrong songbook is a
  reporting error rather than a cosmetic one. Change a count and pin it with a test.
- **The two files on disk are a compatibility contract.** `statistics.json` and `play_log.json` are
  append-only across years — a church three years in has every service it has ever run in them, and
  neither can be reconstructed if lost. The manager is deliberately forgiving on load, so a field
  renamed here reads back as its default and shows up as a report of *zero* rather than an error.
  `StatisticsFileFormatTest` is what stands between that and a silent loss of history.
- **POI here is HSSF only** — the legacy `.xls` writer, which lives in POI core. This module never
  touches `poi-ooxml`, so the "exactly ONE POI schema jar" rule in the root `AGENT.md` does not
  reach it and no `exclude` belongs in this build file. Keep it that way: reaching for `XSSF` makes
  this module a fourth party to that problem.
- **`StatisticsManager` carries a `@Suppress("TooManyFunctions")`** at its declaration. Twenty
  functions, threshold eleven, and every one has a production caller — it is one pair of files read
  five ways (load/save, record, query, clear, export). The type cannot usefully be split because all
  of them share its `lock`: handing the queries or the export their own object would either
  duplicate that lock or let a reader run against a tally another thread is mid-write on.
  Documented at the site rather than in a baseline; this module has no baseline and must not
  acquire one.
- **No data class belongs in this module.** They live in `:core-models`; `ModelInvariantsTest`
  there discovers them from the compiled output and exercises construction, `copy`, `equals`,
  `hashCode` and destructuring on each, so a model added there is covered without a new test.
- **Only three members are public that would rather not be.** `ROLLING_MONTHS`, `resolveDates` and
  `availableYears` were `internal` while this lived in `:composeApp` and are public purely because
  `CCLIReportDialog` reads them from outside the module. Everything else that was `internal` still
  is — the tests are friends of `src/main`, so widening for a test is never the reason.

## Consumers

`:composeApp` only — `CCLIReportDialog` (the window), `main.kt` and `MainDesktop.kt` (construction),
`BibleTab`/`SongsTab`/`SongsViewModel`/`PlanningCenterImportViewModel` (recording a go-live),
`RemoteApply` (recording a remote go-live) and `VerseSequenceLog`.

## Commands

```bash
./gradlew :statistics:test                              # 119 tests
./gradlew :statistics:detekt                            # no baseline — every finding gates
./gradlew :statistics:jacocoTestCoverageVerification    # the default 85% on all six counters
```

Coverage floors are the defaults and there are **no `coverageExcludes`** — the reported number is
over all nineteen classes.
