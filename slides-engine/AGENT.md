# `:slides-engine` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**What the picture and presentation outputs draw, without the tabs**: `PicturePresenter` (a picture
with its transition), `PresentationPresenter` (a deck's animated `PresentationFrame`, which is
`:output-api`'s), `PictureDecoder` (what Skia refuses, through ImageIO -- HEIC, CMYK JPEGs, PSDs)
and `reportDegradedSlide`.

An engine-layer module (root AGENT.md, "Layers"): `:slides` (the tabs, with `api`), `:presenter`,
`:canvas-engine`, `:live-output`, `:server` and `:profiles` use it. It takes `:output-api`,
`:presentation-engine`, `:shared-ui`, `:strings`, `:core-models`, `:settings` and `:diagnostics`.

**Packages are `:slides`' own** (`org.churchpresenter.slides.presenter`, `.utils`): the files moved
here unchanged, so no import moved with them.

## Tests

The presenters' and the decoder's suites moved with them. `tempDir` and `solidImage` (test
fixtures) are the file helpers they and `:slides`' suites share; the screenshot suites stay with
the tabs.

## Commands

```bash
./gradlew :slides-engine:test :slides-engine:detekt
./gradlew :slides-engine:jacocoTestCoverageVerification
```
