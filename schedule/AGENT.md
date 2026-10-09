# `:schedule` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Schedule** tab: the running order in the side panel, with its rows, notes, timing, drag to
reorder, files dropped in from outside the app, the toolbar, and the calendar notices under Add
Files. `ScheduleViewModel` lives here, along with the `.cps` files it opens, saves and autosaves.

A real Gradle module of this build: `include(":schedule")`, `implementation(projects.schedule)`.
`:composeApp` is its only consumer. Besides the tab it uses `ScheduleViewModel` (the menu, remote
commands, the calendar, Instance Link) and `LocalOpenCalendar`.

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme`, `:calendar`,
`:diagnostics` and `:show-control` (a row's cue actions), and nothing of `:composeApp`'s.

## Seams to the app

- **`planningCenterImport`**: a slot for the Planning Center import dialog, which the app supplies
  from `ScheduleSidebar`. The dialog stays in the app.
- **`followRemoteSchedule(items)`**: the app maps the primary's Instance Link broadcast to
  `ScheduleItem`s and logs the sync (`applyRemoteSchedule` in `remote/RemoteScheduleMapping.kt`).
  The DTO mapping tests stay in the app.
- **Cue actions** (`docs/SHOW_CONTROL.md`): each row's show-control actions live in `actions`, beside
  `notes` and `timing`, in the file (`ScheduleFileV2.actions`, left out when empty) and in undo. The
  row's Actions button opens `RowActionsDialog`; what its pickers offer comes from the app through
  `LocalActionChoices`. `presentItem` hands a row's actions to `onRowActions` after its content;
  the app runs them.
- **`ScheduleViewModel(autoSaveIntervalMs = …)`** is defaulted, so the app never passes it. Tests
  shorten it to drive the autosave loop.

## Layout

- **`ScheduleViewModel`** keeps its state, lifecycle and the few primitives every edit goes
  through (`addOrPush`, `pushUndoSnapshot`, `replaceSchedule`). Everything else is an extension on
  it, one file per concern: `…Add`, `…Edits`, `…Order`, `…Selection`, `…History`, `…Files`,
  `…AutoSave`.
  - Outside the module the extensions need an import (`import org.churchpresenter.schedule.addSong`).
- **`ScheduleTab`** composes the pieces: `ScheduleHeader`, `ScheduleRowList` (rows, drag state,
  drop overlays) and the footer. `ScheduleItemRow` draws one row, with its parts in
  `ScheduleItemRowParts.kt`. `ScheduleTabActions` and `tabActions` are in `ScheduleTabActions.kt`.

## Package

**`org.churchpresenter.schedule`**. The tab harness (`scheduleTab`, `ScheduleLabel`, the finders) is
in `src/testFixtures` and is `internal`. Only `seedEveryItemType` is public, because the app's
website export (`AppPreviewScheduleScreenshotTest`) seeds the panel with it.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here.** The screenshot suite is `screenshot/ScheduleTabScreenshotTest`,
  and its images are under `schedule/screenshots/scheduleTab/`. The website's
  `previewApp/schedule_*` export is shot by the app, beside the other `previewApp` images.
- What needs the app stays there: `ScheduleSidebar`, `ScheduleActions`, the go-live tests
  (`ScheduleSongGoLiveTest`) and the remote mapping tests.

## Commands

```bash
./gradlew :schedule:test :schedule:detekt
./gradlew :schedule:jacocoTestCoverageVerification
./gradlew :schedule:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :schedule:verifyRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :schedule:pitest                      # drag math and the file cipher only
```

**Mutation testing** covers only the pure logic (`ScheduleDragMath`, `ScheduleCipher`, against
`ScheduleDragMathTest`, `ScheduleDragMathPropertyTest` and `ScheduleFileTest`): the tab's UI tests
would run once per mutant. Score 88% (15 of 17 killed, 2026-10-08); `mutation-test.yml` runs it
weekly, advisory.
