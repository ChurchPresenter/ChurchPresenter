# Wick pack

What Wick can learn without an app release. The app downloads `pack.json` from `main` at most once a
day, caches it, and falls back to the cache, then to its bundled data, when it cannot.

- **Edit `source.json`, never `pack.json`.** Then run `./gradlew :helper:buildWickPack`, which checks
  every target and embeds every phrase with Wick's sentence model, and commit both files.
- **Raise `version`** with every change. `minApp` is the oldest app version the pack is for, as the app
  writes its own: `26.15.0`, not `2026…`.
- **Data only:** phrases that lead to things Wick already does (a `catalog.tsv` target — `request:…`,
  `suggested:…`, `control:…`, `settings:…`, `tab:…`, `shortcut:…`), tours over controls the app already
  tags (`GuideTargets`), and tips. A step's `before` is `tab=SONGS`, `settings=PROJECTION` or
  `profile=SONGS` (`profile=SONGS:<row string key>`). New rules and actions need an app release.
- **English only.** Hints and tips are shown as written; tips only in an English app.
- Anything the app does not know is dropped when the pack loads, so an older app never breaks on it.
- A chat someone sent is never committed to this repo or used as test data.
