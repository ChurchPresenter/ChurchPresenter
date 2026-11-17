# `:bible-editor` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Bible editor**: a window for restoring the verses a translation is missing and fixing clear
typing mistakes, opened from the **Developer** menu (dev mode only). A real Gradle module of this
build — `include(":bible-editor")`, `implementation(projects.bibleEditor)`.

It **never rewrites a `.spb` file.** Every change is an entry in one history file,
`bible-edits.json`, in the folder the app hands it (the app's data folder). What a verse reads as
now is the last entry for it, so discarding is cutting the history back to what was saved. The
change model and that file are `:bible-edits`, which the Bible tab also reads: a save here calls
`onSaved`, and the app reloads the Bible tab with the corrections laid over each translation.

## What `:composeApp` uses from it

`BibleEditorApp(bibleFolder, dataFolder, onClose, preferred, onSaved)` — opened by
`BibleEditorWindow` in `dialogs/ToolWindows.kt`.

## Layout

`src/main/kotlin/org/churchpresenter/bibleeditor/`

| file | owns |
|---|---|
| `EditHistory.kt` | the history in the window: commit, revert, save point, discard |
| `BibleEditorState.kt` | what is open, what is being typed, loading on `io` |
| `ChapterRow.kt` | the open chapter as rows, the verse actions, the shelf scan |
| `MissingVerse.kt` | verses the reference (the shelf's KJV) has and this Bible lacks, by verse code |
| `WordDiff.kt` | the word diff the editor and the history show |
| `ui/` | the window: header, chapter, verse rows, history panel, missing-verses dialog |

## Rules

- **No palette of its own.** `EditorColors` resolves every color from `MaterialTheme.colorScheme`
  and `:theme`'s `MaterialTheme.semantic`, so the window follows all nine themes.
- Files are read and written on `io` only; composables read state.
- `clock`, `tickClock` and `LocalScriptureFont` exist so a test pins the time and the verse face;
  the app never passes them.

## Commands

```bash
./gradlew :bible-editor:test
./gradlew :bible-editor:detekt
./gradlew :bible-editor:jacocoTestCoverageVerification
./gradlew :bible-editor:recordRoborazziJvm --tests '*ScreenshotTest*'   # images in bible-editor/screenshots/
./gradlew :bible-editor:verifyRoborazziJvm --tests '*ScreenshotTest*'
```

## Tests

`EditorFixture` writes a KJV and an "RST" whose Matthew 17 lacks verse 11 and has two typos. The UI
suites open the window through `withEditor` in a window tall enough that the whole chapter is laid
out (a lazy list composes only what shows); the screenshots use the real 1240×820.

The screenshots exist although the editor is dev mode only: they were asked for by the person
running the work, against the root rule that dev-mode features are not shot.

## Gates

INSTRUCTION, LINE, METHOD and CLASS clear 98%. BRANCH (0.81) and COMPLEXITY (0.78) are under the
85% floor: what is left is the recomposition branches Compose emits around remembered lambdas.
No floor is lowered and nothing is excluded until the person running the work decides.
