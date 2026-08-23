# `:dictionary` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The bundled study data behind the Dictionary tab: the Strong's dictionary itself — 14,197 entries
across Hebrew and Greek, in English and Russian — and the interlinear index that says where every
one of those numbers occurs in scripture. Six JSON files totalling **18 MB**, the loading of them,
and the lookups over them.

It is data and queries only. The tab that draws it and the view model that holds its UI state are
`:dictionary-tab`; the REST routes that serve it are `:companion-server`. This module has neither
Compose nor Ktor, and must not gain either — `:companion-server` depends on it, so a Compose
dependency here would drag Compose into the server.

Not to be confused with the Bible modules it sits beside:

| module | what it does |
|---|---|
| `:dictionary` | *this one* — Strong's entries and the interlinear index over them |
| `:bible` | a loaded `.spb` translation, its books, verses and search |
| `:bible-engine` | speech-to-reference detection (the auto-follow feature) |
| `:bible-formats` | the download catalogues, and converting USFX/Zefania **into** `.spb` |
| `:dictionary-tab` | the Dictionary tab itself — the browser, the view model, the presenter |

A real Gradle module of this build: `include(":dictionary")`, `implementation(projects.dictionary)`.

## What the rest of the build uses from it

- `StrongsEntry` — the entry itself, held by `DictionaryViewModel` and `DictionaryPresenter` in
  `:dictionary-tab`, and by `PresenterManager`, `StageMonitorScreen` and `RemoteApply` in
  `:composeApp`.
- `StrongsCatalog` — `DictionaryViewModel` takes one as a constructor parameter and calls `load`.
- `InterlinearRepository` — `DictionaryViewModel` holds one for the "In Scripture" panel.
- `InterlinearVerse` / `InterlinearWord` — what that panel renders.
- `StrongsDictionaryRepository.shared` plus `StrongsEntryDto`, `DictionaryVerseDto` and
  `DictionaryVersesResponse` — `BibleRoutes` serves the three `/api/dictionary` endpoints from them.

## Layout

`src/main/kotlin/org/churchpresenter/dictionary/` — note the package is **not**
`org.churchpresenter.app.churchpresenter.*`.

| file | owns |
|---|---|
| `StrongsEntry.kt` | one dictionary entry, and how its number is read |
| `InterlinearVerse.kt` | one indexed verse and its words, with the packed `BBBCCCVVV` reference |
| `StrongsCatalog.kt` | reading and parsing the four dictionary files, and the classpath reader |
| `InterlinearRepository.kt` | the two interlinear files and the five lookup tables built from them |
| `StrongsDictionaryRepository.kt` | search/lookup/occurrences for the REST layer, and its DTOs |

`src/main/resources/dictionary/` — the six bundled files. They are **plain classpath resources, not
Compose resources**: nothing here draws anything, so the module needs no Compose plugin, and keeping
them off `composeResources` also keeps 18 MB of generated resource accessors out of the coverage
numbers. `readBundledDictionaryFile` is the only thing that knows where they are.

## Nothing loads until something asks

Every entry point here is lazy, and deliberately: the Greek interlinear file is 4 MB and the Hebrew
one 8 MB, most sessions never open the panel that needs either, and a session in English never reads
the Russian dictionary at all. `InterlinearRepository` loads each testament at most once and
independently; `StrongsDictionaryRepository` caches per language behind a mutex.

`StrongsCatalog` caches **nothing**. Its two callers want different things from a load — the tab
sorts each half by number and holds the result as UI state, the REST layer caches both halves flat —
so what they share is the reading and parsing, and each keeps what it made of it.

## The loader parameter is the seam — do not add a mutable one

`StrongsCatalog` and `InterlinearRepository` each take a `(String) -> ByteArray` with the packaged
reader as its default. Tests pass a lambda over a handful of entries: the production parsing,
indexing, filtering and sorting all really run, over a corpus small enough to assert on by name, and
nothing has to be reset afterwards because nothing is shared. **Do not replace this with a mutable
`internal var` on a singleton**, which is what the app-side version of these classes needed before
the extraction — a `resetForTest()` on each, called from four suites, and a load-once cache that one
test could leave stubbed for the next.

`StrongsDictionaryRepository` is a class for the same reason. The app takes the one instance it
wants from `StrongsDictionaryRepository.shared`; a test constructs its own with a fixture catalogue.

## Test fixtures published from here

`DictionaryFixture` and `RecordingFiles` (in `src/testFixtures`) are the four-entry dictionary and
the five-verse interlinear index. They live here because this module owns the files they stand in
for, and `:composeApp`'s dictionary tab, view-model and screenshot suites take them from here —
`testFixtures(projects.dictionary)` — so the app and the module assert against one corpus rather
than two. `RecordingFiles` counts reads, which is how "each testament is read once" and "a Greek
lookup does not touch the Hebrew file" can be asserted at all.

## Gates

- **Coverage**: the root build's default six counters at 85%, **no `coverageFloors` override and no
  `coverageExcludes`**. It landed at instructions 99.3 / branches 94.4 / lines 99.5 / complexity
  97.2 / methods 100 / classes 100. Branch coverage is the tight one; a new branch here needs a test
  with it. **Do not answer that by writing a floor into this file.**
- **Detekt**: `./gradlew :dictionary:detekt`, **no baseline**, clean, and `src/testFixtures/kotlin`
  is scanned along with main and test. **Do not add a baseline file to this module.**
- One test reads the real packaged files: `StrongsCatalogTest.a default catalogue loads the packaged
  dictionary`. It is the only thing standing between a renamed or unpackaged resource and a
  dictionary tab that silently shows nothing — `DictionaryViewModel.load` swallows the exception —
  so keep it, and keep it asserting on the shipped data rather than on a fixture.

## Commands

```bash
./gradlew :dictionary:test
./gradlew :dictionary:detekt
./gradlew :dictionary:jacocoTestCoverageVerification
```

## Dependencies

kotlinx-coroutines and kotlinx-serialization-json, and nothing else. No Compose, no Ktor, no other
module of this build.
