# `:songs` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Songs** tab: the song list with its search, songbook filter, columns and favorites, the lyrics
panel, and the keyboard navigation that pushes sections and lines to the output. `SongsViewModel`
and the library on disk (`Songs`, the `.song` folders and Mac SongPresenter `.sps` files) live here,
and so does the **song editor**, `EditSongDialog`, with its chord preview and chord picker.

A real Gradle module of this build: `include(":songs")`, `implementation(projects.songs)`.
`:composeApp` is its only consumer. Besides the tab it uses `SongsViewModel` (Planning Center
import, the tool windows, Instance Link mirroring) and `Songs` (`CompanionLibraryFeed`,
`SpsConverter`).

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme`,
`:song-chords`, `:presenter` (the editor's chord preview draws `ChordLine`) and `:calendar` (its
footer's `formatDuration`), and nothing of `:composeApp`'s. Its tests also take `:profiles`, to draw
the real Background button in the editor's slot.

## Seams to the app

The tab and view model take what the app owns as parameters:

- **`songEditor`**: the slot the song editor is drawn in, handed a `SongEditorRequest`. The app's is
  `AppSongEditor`, which opens this module's `EditSongDialog` and decides whether the tempo field
  shows.
- **`EditSongDialog`'s `backgroundButton`**: the editor's Background button, handed a
  `SongBackgroundButtonState`. The app draws `:profiles`' `SongBackgroundButton` in it
  (`songEditorBackgroundButton`); the tests draw the same through `testBackgroundButton`.
- **`titleSlideFor`**: builds the title slide. The app passes `titleSlideSection`, which lays the
  lines out the way the presenter draws them. Tests use `fakeTitleSlide`.
- **`onSongWentLive`**: called once per different song that goes live. The app records statistics
  and usage telemetry there (`recordSongWentLive`) and starts the song's duration measurement.
- **`playCounts`**: a `SongPlayCounts`, used by the Plays column and its sort. `StatisticsManager`
  implements it.
- **`SongsViewModel.setInstanceLinkSource`** takes the primary's catalog as `SongItem`s and a fetch
  that returns a song's raw lyrics. The app converts its wire DTOs (`RemoteSongCatalog.kt`).
  `remoteSyncLog` is where the follower's sync events go: `InstanceLinkLogger` in the app.

## Layout

- **`SongsViewModel`** keeps its state and lifecycle (load, dispose, settings, favorites, the
  Instance Link source). Everything else is an extension on it, one file per concern:
  `SongsViewModelSelection`, `…Navigation`, `…Sections`, `…Filtering`, `…Editing`, `…Loading`. The
  state those files touch is `internal` and named `…State` (no `_` backing fields), so the class
  stays under detekt's function limit without changing a single call site in the module.
  - Outside the module the extensions need an import (`import org.churchpresenter.songs.selectSong`).
- **`Songs`** is the library. The `.sps` format, text and SQLite, is in `SpsFormat.kt`.
- **The tab** remembers a `@Stable` `SongsTabController`: the live song, the dialogs, the panel
  sizes, and every push to the output. `SongsTab` assigns its parameters to it on each composition.
  The pieces are extension composables on it, in `SongsTabParts.kt`. The keys are in
  `SongsTabKeys.kt`.
  - **Anything a piece reads while composing is snapshot state on the controller.** A plain `var`
    is not re-read when the tab recomposes, because strong skipping skips a piece whose receiver is
    the same instance. Only callbacks that are just invoked are plain fields.

## Package

**`org.churchpresenter.songs`**. The tab harness (`songsTab`, `SongFixture`, `TabReports`,
`fakeTitleSlide`, the finders) is in `src/testFixtures`. It is public so `:composeApp`'s suites can
drive the tab with the app's own seams filled in.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here**, beside the code. The screenshot suite is
  `screenshot/SongsTabScreenshotTest`, and its images are under `songs/screenshots/songsTab/`. The
  editor's are `screenshot/EditSongDialogScreenshotTest`, under `songs/screenshots/editSongDialog/`.
- **The editor is split by concern**, every file under detekt's function limit: `EditSongDialog`
  (the dialog, `EditSongContent`, the footer), `EditSongState` (the `@Stable` edit buffers and
  every edit, plus the tuning and language-name drafts), `EditSongHeader` (the metadata cards),
  `EditSongEditor` (the pane toolbar, chips, legend), `EditSongLyricsField` and `EditSongText`
  (the pure text helpers).
- The went-live telemetry is the app's (`SongsTabGoLiveTelemetryTest`), because it needs the app's
  `StatisticsManager`.

## Commands

```bash
./gradlew :songs:test :songs:detekt
./gradlew :songs:jacocoTestCoverageVerification
./gradlew :songs:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :songs:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
