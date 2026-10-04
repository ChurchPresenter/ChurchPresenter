# `:presenter` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

What the song and Bible outputs draw:
- `SongPresenter` and `BiblePresenter`, with their looks, frames, slides, layouts and fitting;
- the title slide (`SongTitleSlide`, `titleSlideSection`) and the song and Bible style models
  (`SongElementStyle`, `BibleElementStyle` and the per-language, visibility, number-corner and
  title-slide-offset access beside them);
- the backgrounds (`PresenterBackground`, `LoopingVideoBackground`) and the lower-third layout;
- the Lottie bands (`BibleLottieBand`, `BibleLottieTemplate`, `SongLottieBand` and their slots,
  text fitting and clock);
- `ChordChart`, the chord rows a song slide draws.

A real Gradle module of this build: `include(":presenter")`, `implementation(projects.presenter)`.
`:composeApp` is its only consumer. It keeps the output windows (`PresenterScreen`,
`PresenterOutputContent`), `PresenterManager` and the transitions that drive the bands, the
settings pages, and the off-screen outputs (NDI, OMT, Browser Source, DeckLink, the scene pump).

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme`,
`:diagnostics`, `:canvas`, `:slides`, `:media`, `:lower-third`, `:lottieGenerator` and
`:song-chords`, and nothing of `:composeApp`'s.

## Package

**`org.churchpresenter.presenter`**; the screenshot suites are in
`org.churchpresenter.presenter.screenshot`.

## Test fixtures

`src/testFixtures` holds `LottieBandTestSupport`, the generated band template the Lottie suites point
the settings at. `:composeApp`'s band and transition suites use it too
(`testFixtures(projects.presenter)`).

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here.** The screenshot suites are `screenshot/PresenterScreenshotTest`,
  `PresenterLowerThirdScreenshotTest`, `PresenterPortraitLowerThirdScreenshotTest` and
  `PresenterTitleSlideScreenshotTest`; their images are under `presenter/screenshots/`.
- What needs the app stays there, with its tests: anything that reaches `PresenterManager`,
  `PresenterTransitionEffects` or `PresenterScreen`, the full-screen screenshot suites (they draw
  the other tabs' presenters too), and the settings UI around the style models.

## Commands

```bash
./gradlew :presenter:test :presenter:detekt
./gradlew :presenter:jacocoTestCoverageVerification
./gradlew :presenter:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :presenter:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
