# `:announcements` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Announcements** tab: an announcement's text and look, and its timer (countdown, count-up,
specific time or clock). The tab, `AnnouncementsViewModel` and `AnnouncementsPresenter` (`presenter/`,
the announcement on an output) live here.

A real Gradle module of this build: `include(":announcements")`,
`implementation(projects.announcements)`. `:composeApp` is its only consumer. `PresenterManager`
uses `AnnouncementsViewModel.formatTimer`.

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings` and `:theme`, and nothing of
`:composeApp`'s.

## Seams to the app

- **`AnnouncementsOutput`** is what the tab needs of the output: presenting, screen locks, the text,
  and the timer.
  - The timer ticks on the output, not in the tab, so a countdown keeps running while the operator
    is on another tab.
  - The app implements the interface as `PresenterAnnouncementsOutput`
    (`presenterManager.announcementsOutput`).
  - Tests use `FakeAnnouncementsOutput`. It makes the same state changes but never ticks.
    `AnnouncementsTimerControlTest` in the app drives the real ticker.
- **The app passes in:**
  - `previewOutput`, the output the preview stands for;
  - `stageMonitorScreens`;
  - an `outputPicker` slot.

  `AppAnnouncementsTab` works all three out.
- **`use24HourClock`** defaults to the system's choice, and the test harness pins it. A value from
  outside the composition is taken as a parameter so tests read the same on every host.

## Layout

- **`AnnouncementsViewModel`** keeps its state in `AnnouncementsState` and delegates the timer to
  parts in `AnnouncementsTimerParts.kt`:
  - the duration;
  - the Specific Time target;
  - the mode and its clock previews;
  - start/pause/reset.

  Plain settings are properties with setters, not `setX` functions. It is marked `@Stable`.
- **The tab** builds a remembered, `@Stable` `AnnouncementsTabScope` from `AnnouncementsTabInputs`
  (what the app hands it) and `AnnouncementsTabEnvironment` (what the composition supplies). The
  pieces are extension composables on the scope, in `AnnouncementsLeftColumn.kt`,
  `AnnouncementsRightColumn.kt` and `AnnouncementsTimerSection.kt`.

## Package

**`org.churchpresenter.announcements`**, with `.presenter`. The test helpers (`announcementsTab`, `FakeAnnouncementsOutput`,
`AnnouncementLabel`, the finders) are in `src/testFixtures`. They are public, so the app's
screenshot suite can use them.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests live here**, beside the code. The tab's screenshots stay in the app
  (`AnnouncementsTabScreenshotTest`), where they were recorded.
- A view-model property forwards to `AnnouncementsState` with a getter and setter, not
  `by state::x`. Property-reference delegation generates accessors that are never called, which
  count against the method floor.

## Commands

```bash
./gradlew :announcements:test :announcements:detekt
./gradlew :announcements:jacocoTestCoverageVerification
```
