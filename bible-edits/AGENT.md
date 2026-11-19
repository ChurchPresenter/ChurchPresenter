# `:bible-edits` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The corrections made in the Bible editor, without the editor's window: the change model, the
`bible-edits.json` history they are kept in, and laying them over a loaded module. A real Gradle
module of this build — `include(":bible-edits")`.

`:bible-editor` writes the history; `:bible-tab` reads it as it loads a translation, so what the
operator presents is the corrected text. The `.spb` files themselves are never rewritten.

## Layout

`src/main/kotlin/org/churchpresenter/bibleedits/`

| file | owns |
|---|---|
| `BibleEdits.kt` | `VerseChange`, `EditEntry`, `VerseAddress`, `EditKind`, replaying the history |
| `BibleEditsStore.kt` | reading and writing `bible-edits.json` |
| `AppliedEdits.kt` | the history for one Bible file as `VerseEdit`s, laid over a loaded `Bible` |

A change is keyed by the Bible file's path inside the Bible folder — the same name the Bible
settings store, so the editor and the Bible tab find the same file by it.

## Rules

- Dev mode only, like the editor: the Bible tab reads the history only while dev mode is on.
- No Compose here; the editor's state over this model lives in `:bible-editor`.

## Commands

```bash
./gradlew :bible-edits:test
./gradlew :bible-edits:detekt
./gradlew :bible-edits:jacocoTestCoverageVerification
```
