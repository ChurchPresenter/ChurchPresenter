# `:qa` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Q&A** tab and the session behind it. It holds:
- `QAManager`, the questions phones send in, what the operator does with them and what is on
  screen;
- `QATab`, the operator's view of that session;
- `QAPresenter` and `QAQRCodePresenter` (`presenter/`), the question and the QR code on an output.
  The QR image is `generateQRCodeBitmap` in `:shared-ui`, which the presentation remote also draws.

A real Gradle module of this build: `include(":qa")`, `implementation(projects.qa)`. Its consumers
are `:composeApp` and `:live-output`. The app's Companion server and the remote dialog drive the
same `QAManager`.

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme` and
`:diagnostics`, and nothing of `:composeApp`'s.

## Seams to the app

- **`QAOutput`** is what the tab needs of the output: what is presenting, the screen locks, and the
  question or QR code to put up. `:live-output` implements it as `PresenterQAOutput` over
  `PresenterManager` (`presenterManager.qaOutput`). Tests use `FakeQAOutput`.
- **`remoteDialog`** is a slot. The remote-access dialog needs the server and the tunnel, so the app
  draws it. `AppQATab` is the app's wrapper that fills it in.

## Layout

- `QAManager` delegates to three parts that share one `QAStore` and its one lock:
  - `QAModeration`: moderation and the display;
  - `QAAudience`: what phones do;
  - `QASessionControl`: starting and ending a session.

  Every change is made under `synchronized(store.lock)`.
- `QATab` builds a remembered `QATabScope` (marked `@Stable`; everything it exposes is snapshot
  state or an input it is rebuilt for). Its pieces are extension composables on that scope, in
  `QATabBars.kt` and `QATabList.kt`. One question's row is in `QuestionRow.kt` and
  `QuestionRowActions.kt`.

## Package

**`org.churchpresenter.qa`**, with `.presenter`. The test helpers (`qaTab`, `FakeQAOutput`, `QALabel`, the finders)
are in `src/testFixtures`. They are public, so the app's screenshot suite can use them.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests live here**, beside the code. The tab's screenshots stay in the app
  (`QATabScreenshotTest`), where they were recorded.
- `QATabScope` must stay honestly `@Stable`. A new property must read snapshot state or be an input
  the `remember` is keyed on.

## Commands

```bash
./gradlew :qa:test :qa:detekt
./gradlew :qa:jacocoTestCoverageVerification
```
