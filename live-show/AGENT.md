# `:live-show` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **layer model** of `docs/LAYER_MODEL.md`:
- `Layer`: the fixed stack every output composes, bottom to top;
- `Cue`: what one layer shows, a typed value per kind of content;
- `LiveShow`: program (on air) and preview (cued), one cue per layer, with `set`, `cue`, `take`,
  `clear` and `clearAll`.

A real Gradle module of this build: `include(":live-show")`, `implementation(projects.liveShow)`.
Its consumers are `:composeApp` and `:live-output`. It takes `:core-models` (the cues carry its
models) and Compose's runtime for snapshot state, and nothing of `:composeApp`'s. A background cue
names whose setting it is (`BackgroundSource`) rather than carrying settings, so each output
resolves it against its own.

## Where the migration stands

Steps 1 to 3 of the note's migration. `PresenterManager.program` is **derived** from the single live
mode by `legacyProgram` in `:live-output`: one content layer for any mode but none, plus the
background layer under Bible and songs. Every output (windows, NDI/OMT/Browser Source, preview
tiles) draws it through `:live-output`'s `OutputLayers` and `CueContent`; the full-screen background
is drawn by the presenters' background-only twins (`SlideBackgrounds.kt`), and a lower third's band
stays with its slide. `LiveShow` itself is not wired in until the content setters write cues.

## Package

**`org.churchpresenter.liveshow`**.

## Rules

- **No composables, no Compose compiler plugin.** State only; the outputs read it.
- **A cue is a value.** Equal cues show the same thing, and every operation that changes nothing
  leaves the map instance alone, so an output never redraws for a no-op.
- **A new cue gets its layer pinned in `CueTest`**, which fails when one is added without it.

## Commands

```bash
./gradlew :live-show:test :live-show:detekt
./gradlew :live-show:jacocoTestCoverageVerification
./gradlew :live-show:pitest                  # mutation score, build/reports/pitest/
```

**Property tests**: `LiveShowPropertyTest` plays generated runs of operator moves and checks every
step against the state before it (pinned seed). **Mutation score** 91% (31 of 34 killed,
2026-10-08); `mutation-test.yml` runs it weekly, advisory.
