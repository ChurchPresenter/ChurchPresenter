# `:output-api` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**What each tab needs of the live output, as interfaces**: `BibleOutput`, `SlidesOutput` (with
`LiveOutput`, `PictureOutput`, `DeckOutput`), `MediaOutput`, `WebOutput`, `QAOutput`,
`AnnouncementsOutput` — and `PresentationFrame` (with `PlacedLayer`, `TransitionOverlay`), the frame
a deck plays, which `SlidesOutput` hands over.

An engine-layer module (root AGENT.md, "Layers"). A tab declares against its port; `:live-output`
implements it (`PresenterSlidesOutput`, `PresenterQAOutput`, …, and `PresenterManager` itself for
`BibleOutput`); `:composeApp` hands the implementation to the tab. Neither the tab nor the output
depends on the other.

**Packages are the tabs' own** (`org.churchpresenter.slides`, `.qa`, `.web`, …): each port moved
here unchanged from the tab that declares it, so no import moved with it.

## Rules

- **A port holds what the tab needs and nothing of how the output does it.** Interfaces, and the
  data types in their signatures. Routing (the preview bus, which manager a slide goes to) is
  `:live-output`'s, in its adapters.
- **A new need from the output is a new member here**, implemented in `:live-output` and faked in
  the tab's own test fixtures (`FakeSlidesOutput`, `FakeQAOutput`, …).
- **No DefaultImpls.** The module compiles with `jvmDefault = NO_COMPATIBILITY`: a port's default
  member is an interface default method and nothing else. The DefaultImpls bridges Kotlin otherwise
  adds exist for Java callers compiled in the old mode, and every caller is Kotlin from this build.

## Commands

```bash
./gradlew :output-api:test :output-api:detekt
./gradlew :output-api:jacocoTestCoverageVerification
```
