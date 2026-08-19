# `:converter` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`; the user-facing description of the tool is in this directory's `README.md`.

## What it is

A song/Bible **format converter**: it reads other presentation software's libraries and writes
ChurchPresenter's own `.sps` songs and `.spb` Bibles. It ships twice — as a window the app opens
from the Help menu, and as its own installable desktop app
(`.github/workflows/converter-installers.yml` packages it).

A real Gradle module of this build: `include(":converter")` in `settings.gradle.kts`, and
`:composeApp` takes it as `implementation(projects.converter)`. Not a submodule, not a mounted
source directory.

## What `:composeApp` uses from it

Only these symbols — keep them public, and treat a signature change here as an app change:

| Symbol | Used by |
|---|---|
| `ui.App` (as `ConverterApp`), `ui.Strings` | `dialogs/AboutDialog.kt` — the Help-menu window |
| `converter.bible.XmlToSpbConverter` | `data/ZefaniaSource.kt`, `data/BebliaSource.kt` |
| `converter.bible.UsfxToSpbConverter` | `data/EBibleSource.kt` |
| `converter.bible.BibleCatalogNaming` | `data/ZefaniaRepositoryIndex.kt`, `data/BebliaCatalogIndex.kt` |
| `converter.bible.BookNames` | `data/BebliaSource.kt` |

The app's own `data/SpsConverter.kt` is a different thing with a similar name — it is app code and
does not live here.

## Layout

`src/main/kotlin/`

| Package | Owns |
|---|---|
| `converter/song/` | One converter per source format, plus the shared lyric/section machinery (`LyricBlocks`, `SectionLabel`, `SongOutput`, the `SongFormatConverter` registry) and format helpers (`ParadoxTable`, `ProtoMessage`, `XmlRepair`, `XmlSupport`, `ChordLines`, `DocumentTextExtractor`) |
| `converter/bible/` | `XmlToSpbConverter` (Zefania/Beblia XML), `UsfxToSpbConverter` (eBible USFX), `BebliaParser`, `BookNames`, `BibleCatalogNaming`, `SpbVersePatcher` + `VersePatches` |
| `converter/library/` | Library-wide passes: `DuplicateFinder`, `RtfText`, `TextUtils` |
| `ui/` | The Compose Desktop GUI (`App`, theme, widgets, `Strings`) |
| `Main.kt` | `mainClass = "MainKt"` — the standalone app's entry point |

Source formats currently handled: SongBeamer `.sng`, OpenLP (`songs.sqlite` and OpenLyrics),
OpenSong, FreeShow, Free Worship, EasySlides, EasyWorship (including schedules), Quelea,
ProPresenter, MediaShout, SoftProjector `.sps`, Markdown, and lyrics extracted from PDF/Word/
PowerPoint documents.

## Commands

```bash
./gradlew :converter:test                              # its suite
./gradlew :converter:run                               # the converter alone, without the app
./gradlew :converter:detekt                            # gate — no baseline, must be clean
./gradlew :converter:jacocoTestCoverageVerification    # the coverage floor
./gradlew :converter:packageDmg                        # installer (Msi/Deb also available)
```

detekt and the coverage floor are their own CI steps, gated on this directory changing plus the
shared build files — see the `converter` filter in `.github/workflows/test.yml`.

## Gates

- **detekt**: same `config/detekt/detekt.yml` as the app, **no baseline**, and everything it
  analyzes is clean — keep it that way. `source` is set to `src/main/kotlin/converter` and
  `src/test/kotlin`, so `ui/**` is deliberately out of scope: it is the pre-existing Compose GUI,
  the kind of code `:composeApp` keeps in its own baseline rather than gating. **Everything that
  parses a file is analyzed.**
- **Coverage** (the root build's six counters — see the root `AGENT.md`): `extra["coverageFloors"]`
  lowers BRANCH to 0.80 and COMPLEXITY to 0.75 because they cannot reach 85 here; the other four
  stay at the 85% default. `extra["coverageExcludes"]` drops `ui/**` and `MainKt*` — they need a
  display. Both `extra` blocks must stay **above everything else** in the build file, and the
  module must never re-declare the JaCoCo tasks themselves.

## Dependencies

- POI: `poi-ooxml:5.3.0` **with `poi-ooxml-lite` excluded** plus `poi-ooxml-full:5.3.0`. This jar
  is on the app's classpath, and **exactly ONE POI schema jar may be there** — the same exclusion
  is mirrored in `composeApp/build.gradle.kts` and in the Presentation Engine.
- `pdfbox:2.0.33` for document text extraction, `sqlite-jdbc` for the OpenLP/MediaShout databases,
  `kotlinx-serialization-json` for the JSON-shaped formats.
- Versions come from `gradle/libs.versions.toml` where the catalogue has them; the POI/PDFBox
  literals here are pinned deliberately and must match the app's.
