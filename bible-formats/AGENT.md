# `:bible-formats` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**Getting a Bible module onto disk**: the catalogues the app browses, the downloads behind them, and
the converters that turn what arrives into the `.spb` format the app reads. A real Gradle module of
this build: `include(":bible-formats")`, `implementation(projects.bibleFormats)`.

Two halves that were always one job, split across a module boundary until now:

| Package | Owns |
|---|---|
| `org.churchpresenter.bibleformats` | The format converters — `XmlToSpbConverter` (Zefania XML), `UsfxToSpbConverter` (eBible USFX), `BebliaParser`, `BookNames`, `SpbVersePatcher`/`VersePatches`, `BibleCatalogNaming` |
| `…bibleformats.catalog` | The sources and the installer — `BibleSource` and its three implementations (`EBibleSource`, `ZefaniaSource`, `BebliaSource`), the two index caches, `BibleInstallSupport`, `BibleLanguageNames` |

**Why they had to move together.** The catalogue sources import the converters, and the converters
lived in `:converter` — a Compose **application** module. Extracting only the download half would
have left a headless library depending on a desktop app; taking both inverts it, and `:converter`
now depends on this module for the conversions it offers from its own window.

## Rules

- **No Compose, and no dependency on `:composeApp`.** This module is reachable from a headless
  context; the download browser UI stays in the app.
- **Anything `:composeApp` or `:converter` calls has to be public.** `BibleInstallSupport` was
  `internal` and is not any more — `internal` no longer reaches either consumer.
- **`.spb` is the app's on-disk Bible format**, so a change to what the converters emit is a change
  to files users already have. `ZefaniaConversionTest`/`UsfxConversionTest` pin the output; treat a
  diff there as a compatibility question, not a test to update.
- **Suppressions here are deliberate and were decided, not defaulted to:**
  - `@Suppress("LongMethod")` on `installZefania`/`installEBible`/`installBeblia` — download →
    convert → install is one pipeline and a split buys no test seam.
  - `@Suppress("TooGenericExceptionCaught")` on the five catalogue objects — a fetch spans HTTP, zip
    extraction and JSON parsing, so the throwable set is open and all of it has to become an outcome
    rather than take the download browser down.
  - `@Suppress("TooManyFunctions")` on `BibleInstallSupport` and `ZefaniaRepositoryIndex`, the same
    way `AtemClient` carries one.

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**, main and test both in scope.
  18 of its findings used to sit in `:composeApp`'s baseline; they are suppressed at the site now,
  with the reasons above.
- **Coverage**: the root build's default six counters at 85%, all of them — **no `coverageFloors`,
  no `coverageExcludes`.** COMPLEXITY is the tight one (85.3%), so a new uncovered branch is what
  will break the gate first.

  Reaching it needed real tests rather than a lowered floor. The ones that closed the gap are worth
  knowing about because they cover things nothing else did:
  - `CatalogDtoConstructionTest` — the `@Serializable` DTOs' plain constructors and `equals`. Only
    the masked decoding constructor was ever exercised, and `TreeEntry`'s `path`/`type` are adjacent
    `String`s: swapped, every entry filters out as "not a blob" and the tab comes up empty with
    nothing logged.
  - `EBibleCatalogCacheTest` — the memory → disk → network order, asserted by counting requests
    against an injected clock, so nothing waits.
  - `ZefaniaPathLanguageTest` — the path-derived language fallback for modules that declare none,
    including the Ukrainian conversions the archive files under `RUS`.

## Tests

- **Nothing here touches a real catalogue.** Every fetch goes through `MockEngine`; the install
  suites build a real zip in a temp directory and convert it end to end, with no mocks.
- **`fetchCatalog`/`fetch` take their clock, http client and cache file as parameters**, so TTL
  expiry is arithmetic. Do not reach for a real clock or `Thread.sleep` to age a cache.
- **`BibleCatalogNamingTest` used to exist twice** — 14 tests in `:converter` and 9 in
  `:composeApp`, written independently against the same class. The extraction merged them; the 14
  are the superset and the 9 were re-tests of the same behaviours.

## Commands

```bash
./gradlew :bible-formats:test
./gradlew :bible-formats:detekt                            # gate — no baseline, must be clean
./gradlew :bible-formats:jacocoTestCoverageVerification
```

All three run in CI, gated on this directory or the shared build files changing.

## Dependencies

`api(libs.ktor.client.core)` — every fetch takes an `HttpClient`, so the type is part of the public
surface. Then `ktor-client-cio`, `kotlinx-serialization-json` (Beblia's manifest), `:settings` for
the app data directory constant, and `:diagnostics` for `CrashReporter`. No Compose.
