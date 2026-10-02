# `:dictionary` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Strong's dictionary**:
- the tab (`DictionaryTab` and its panes);
- `DictionaryViewModel`;
- the interlinear index (`InterlinearRepository`);
- the data both read: the Strong's dictionaries, in English and Russian, and the Greek and Hebrew
  interlinear files.

A real Gradle module of this build: `include(":dictionary")`, `implementation(projects.dictionary)`.
`:composeApp` is its only consumer. Its companion server serves the same data through its own
`StrongsDictionaryRepository`, and its presenter and stage monitor show a `StrongsEntry`.

It takes `:shared-ui`, `:strings`, `:icons`, `:settings`, `:theme` and `:bible`, and nothing of
`:composeApp`'s.

## The data

- **Where it lives:** the six JSON files are under `src/main/resources/dictionary/`, about 18 MB in
  all. They are plain classpath resources, so the module generates no `Res` class of its own.
- **How it is read:** everything goes through **`DictionaryFiles`**. The app reads
  `DictionaryFiles.Bundled`. A test passes `DictionaryFixture.files()`, a handful of entries it can
  see, and reads from that fixture can be counted, failed or held open.
- **Never stub the reads:** don't mock the reader or the files. Hand the fake in through the
  `DictionaryViewModel`, `InterlinearRepository` or `StrongsDictionaryRepository` constructor.

## Layout

- **`DictionaryViewModel`** delegates its functions to three parts. They share one
  `DictionaryState`:
  - `DictionarySelection`: choosing an entry, and the history;
  - `DictionaryPassages`: the passage filters;
  - `DictionaryBibles`: the Bible picker.

  It is marked `@Stable`, because every property reads snapshot state in `DictionaryState`.
- **The tab** is spread over four files:
  - `DictionaryTab.kt`: the frame and its divider;
  - `DictionaryListPane.kt`: the entry list and its filters;
  - `DictionaryDetailPane.kt`: the chosen entry;
  - `DictionaryInScripture.kt`: where the entry occurs.

  The shared controls are in `DictionaryControls.kt`.

## Package

**`org.churchpresenter.dictionary`**, with `.data`. The test helpers (`dictionaryTab`,
`DictionaryFixture`, `FixtureFiles`, `DictionaryLabel`, the finders) are in `src/testFixtures`. They
are public, so the app's screenshot suite and server tests can use them.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests live here**, beside the code. The tab's screenshots stay in the app
  (`DictionaryTabScreenshotTest`), where they were recorded.

## Commands

```bash
./gradlew :dictionary:test :dictionary:detekt
./gradlew :dictionary:jacocoTestCoverageVerification
```
