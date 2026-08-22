# `:bible` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The Bible itself: one loaded `.spb` module — its books, its verses, the two numberings it carries,
and the search over them. Reading a translation off disk and answering questions about it, and
nothing else.

It is **not** the other three Bible modules, and the names are easy to confuse:

| module | what it does |
|---|---|
| `:bible` | *this one* — a loaded translation, its books, verses and search |
| `:bible-engine` | speech-to-reference detection (the auto-follow feature) |
| `:bible-formats` | the download catalogues, and converting USFX/Zefania **into** `.spb` |
| `:core-models` | `SelectedVerse` — what the app has selected, not what the file holds |

A real Gradle module of this build: `include(":bible")`, `implementation(projects.bible)`.

## What `:composeApp` uses from it

`Bible` (loading and every query), `BibleBook`, `BibleVerse`, `BibleSearch`, `ChapterResult`,
`BibleLoadError`, and `readTranslationTitle`/`bibleDisplayNames` for the translation picker.
Fifteen files in the app import it, most of them `BibleViewModel*`.

Two things that look like they belong here and deliberately do not:

- **`BibleBookNames` and `BibleBookAbbreviations` stay in `:composeApp`.** They resolve Compose
  string resources, and this module has no Compose on its classpath by design.
- **`BibleFolderListing` stays too**, because it goes through the app's `FileManager`.

## Layout

`src/main/kotlin/org/churchpresenter/bible/` — note the package is **not**
`org.churchpresenter.app.churchpresenter.*`; `:settings`, `:diagnostics` and `:atem` each recorded
that trap before this module existed.

| file | owns |
|---|---|
| `Bible.kt` | the loaded module: loading, the query API, search, and `BibleLoadError` |
| `SpbFormat.kt` | the `.spb` format itself — the six helpers that are pure functions of the file |
| `BibleBook.kt`, `BibleVerse.kt`, `BibleSearch.kt` | the three row types the API hands back |
| `BibleTranslationNames.kt` | reading a module's title without loading it |

## Test fixtures published from here

`SpbFixture` (in `src/testFixtures`) writes real `.spb` files. It lives here because this module
owns that format, and **thirty-three suites in `:composeApp` build their fixtures with it** —
`testFixtures(projects.bible)`. Do not copy it into a suite; take it from here.

## Gates

- **Coverage**: the root build's default six counters at 85%, **no `coverageFloors` override and no
  `coverageExcludes`**. The extraction landed at branches 0.78 / complexity 0.79 and was brought up
  with real tests rather than a lowered floor — `BibleSearchScopeTest`, `BibleMalformedModuleTest`
  and `BibleLookupFallbackTest`. Branches and complexity are the tight pair, clearing by under a
  point, so a new branch here needs a test with it. **Do not answer that by writing a floor into
  this file.**
- **Detekt**: `./gradlew :bible:detekt`, **no baseline**, clean. Two suppressions are written at the
  declarations that carry them, both with reasons, and both are judgement calls a reviewer may
  overrule:
  - `TooGenericExceptionCaught` on the two load paths. Their whole contract is that they never
    throw — a folder of translations is loaded together and one bad file must not take the rest of
    the shelf — so narrowing the catch is a behaviour change, not a tidy-up. An earlier attempt to
    narrow it added an `IllegalStateException` arm that turned out to be **unreachable**: the parser
    skips a line that does not match rather than failing on it.
  - `TooManyFunctions` on `Bible`. See the note at the class.
- **Do not add a baseline file to this module.**

## Commands

```bash
./gradlew :bible:test
./gradlew :bible:detekt
./gradlew :bible:jacocoTestCoverageVerification
```

## Dependencies

`:diagnostics` only, for `CrashReporter` — a module that will not read reports itself rather than
handing the caller a callback to remember. No Compose, no Ktor, no serialization.
