# `:calendar-core` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Calendar Manager's model, without its window**: a planned service and its run of show, the
clocks and timings the planner and the Schedule tab show, the cue engine's rules, the Bible
reference parser, the preset names and the run-of-show PDF export — everything under
`org.churchpresenter.calendar.model` — plus the three small types the Schedule tab reads off the
calendar's engines: `CueFeed`/`FiredCue` (what has fired this session) and `ScheduleServiceLink`
(the service the Schedule holds), and `CalendarBibleBook`.

An engine-layer module (see the root AGENT.md, "Layers"): `:schedule`, `:helper` and `:songs` read
it, and `:calendar`, the window, depends on it with `api`, so whatever takes `:calendar` sees it
too. It takes `:core-models` and nothing else of the app's; no Compose.

**Packages are `:calendar`'s own** (`org.churchpresenter.calendar.model`, and
`org.churchpresenter.calendar` for the four types above): the files moved here unchanged, so no
import moved with them.

## What stays in `:calendar`

The window and everything it draws (`ui/`), `CalendarState`, the stores (`CalendarStore`,
`PresetStore`, `CalendarFileWatcher`), the engines that run against the host (`CueRunner`,
`ServiceAutoLoader`) and calendar sync (`sync/`). `CalendarHost` carries the window's preview
sources, so the engines that take it stay with it; a feature that needs one of their results reads
the type here instead.

## Tests

The model's suites moved with it. `RowSamples` (test fixtures) is the one list of every kind of
row: `RowShapesTest` here and `:calendar`'s `RowSubtitlesTest` both run their passes over it, and
`RowShapesTest` fails until a kind added to `ScheduleItem` is on it.

## Commands

```bash
./gradlew :calendar-core:test :calendar-core:detekt
./gradlew :calendar-core:jacocoTestCoverageVerification
```
