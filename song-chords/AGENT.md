# `:song-chords` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The grammar a song's chords are written in.** Songs are stored with chords inline —
`[G]Amazing [C]grace` — and this module owns every rule about that markup: what counts as a chord,
what counts as a section heading, how a chord transposes and how the result is spelled, and how a
pasted chord sheet becomes markup in the first place. A real Gradle module of this build:
`include(":song-chords")`, `implementation(projects.songChords)`.

| File | Owns |
|---|---|
| `ChordTransposer.kt` | `ChordSegment` and `object ChordTransposer` — the `CHORD` regex, the pitch tables, `parseLine`, `transposeChord`, `detectKey`, `diatonicChords` |
| `SongSectionWords.kt` | `SongSectionWordGroup` and `object SongSectionWords` — the section-heading vocabulary in every language the app reads |
| `ChordSheetImporter.kt` | `object ChordSheetImporter` — turning a pasted chord sheet into inline markup: `isChordLine`, `bracket`, `merge`, `convert` |

## Rules

- **This module depends on nothing.** Not `:core-models`, not `:settings`, not Compose, no
  serialization, no I/O. That is the point: it sits *below* the song model, so everything that reads
  a song can share the grammar without taking a model or a file format along. Adding a dependency
  here is a decision to be argued for, not a convenience.
- **It is deliberately not in `:core-models`.** That module is for the models; this is the rule the
  markup inside one is parsed by. `LyricSection.chordLines` is the field, this is the reader.
- **The chord regex is strict on purpose.** `[Verse 1]` and `[Bridge]` must never parse as chords —
  a heading that does vanishes into the lyric line. Loosening `CHORD` breaks song files that already
  exist, so change it only with a test that pins the headings it must still reject.
- **`ChordTransposer` carries a `@Suppress("TooManyFunctions")`** at its declaration. Thirteen
  functions, threshold eleven, and all thirteen have production callers — they are one grammar read
  three ways, so the object stays whole. Documented at the site rather than in a baseline; this
  module has no baseline and should not acquire one.
- **`:converter` shares this, and used to fork it.** `converter/song/ChordLines.kt` held a
  character-for-character copy of the chord regex and `isChordLine`, because the converter could not
  see `composeApp`. It is gone. Do not reintroduce a second copy — depend on this module instead.

## Consumers

`:composeApp` (`SongsViewModel`, `SongChordPreview`, `AutoFitUtils`, `UsageDetection`,
`EditSongDialog`, `Songs`) and `:converter` (`QueleaConverter`).

## Commands

```bash
./gradlew :song-chords:test                             # 62 tests
./gradlew :song-chords:detekt                           # no baseline — every finding gates
./gradlew :song-chords:jacocoTestCoverageVerification    # the default 85% on all six counters
```

Coverage floors are the defaults and there are **no `coverageExcludes`** — the reported number is
over all five classes.
