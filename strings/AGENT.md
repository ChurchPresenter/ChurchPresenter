# `:strings` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The app's user-facing strings: `values/strings.xml` (English) and every `values-<code>/strings.xml`
locale, and nothing else — no Kotlin source. A real Gradle module of this build —
`include(":strings")`, `implementation(projects.strings)`.

`:composeApp` is its only consumer. `:calendar` and `:songlibrary` keep their own `composeResources`.

## Package

The Compose Resources class is generated into `org.churchpresenter.strings.generated.resources`, set
by `packageOfResClass` in `build.gradle.kts`. Code reads a string as `Res.string.<key>` with both
imported from that package.

Drawables have their own `Res` in `:icons`. A file that needs both imports that one as
`Res as IconRes` (`IconRes.drawable.ic_play`) and leaves `Res` to the strings; the rare file that
also reads `:composeApp`'s fonts or files imports that `Res` as `AppRes`.

## Rules

- **English only.** New keys go in `values/strings.xml`; never add, update or look up a locale file
  (root `AGENT.md`, Translations).
- Removing a key removes it from `values/` **and** every `values-*/` file in the same change.
- The checks on these files — `StringResourceFormatTest`, `LocaleStringsTest`, `LanguageTest` — live
  in `:composeApp`'s `jvmTest` and read the files from this directory.

## Commands

```bash
./gradlew :strings:compileKotlin   # regenerate the Res accessors
```

No tests, no detekt and no JaCoCo: the module has no hand-written code, and the generated accessors
are not what a coverage gate should measure.
