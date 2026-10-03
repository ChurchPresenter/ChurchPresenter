# `:live-show` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **layer model** of `docs/LAYER_MODEL.md`:
- `Layer`: the fixed stack every output composes, bottom to top;
- `Cue`: what one layer shows, a typed value per kind of content;
- `LiveShow`: program (on air) and preview (cued), one cue per layer, with `set`, `cue`, `take`,
  `clear` and `clearAll`.

A real Gradle module of this build: `include(":live-show")`, `implementation(projects.liveShow)`.
`:composeApp` is its only consumer. It takes `:core-models` and `:settings` (the cues carry their
models) and Compose's runtime for snapshot state, and nothing of `:composeApp`'s.

## Where the migration stands

Steps 1 and 2 of the note's migration. `PresenterManager.program` is **derived** from the single
live mode by `legacyProgram` in the app, so exactly one layer is set for any mode but none. Every
output (windows, NDI/OMT/Browser Source, preview tiles) draws it through the app's `OutputLayers` and
`CueContent`. `LiveShow` itself is not wired in until the content setters write cues.

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
```
