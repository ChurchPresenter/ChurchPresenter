# `:signage` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Signage tab**: the lobby screens this ChurchPresenter owns, the players asking
to join, and the **signage folder** their playlists come from. It starts hidden, as STT does
(`AppSettings.hiddenTabs`, settings version 25), and signage runs while it is shown in the tab bar's
show/hide menu. A feature-layer module. It depends
on `:signage-core` for the wire types and the hub, and knows nothing of `:server`: `:composeApp`
gives the same `SignageHub` to both.

## Layout

| File | Holds |
|---|---|
| `SignageController.kt` | Keeps every paired screen's manifest in step with the folder and the tab's choices; runs while the tab is shown in the tab bar, whichever tab is selected |
| `SignageFolder.kt` | Reads the folder: its own files and each folder inside it are playlists |
| `PlaylistSource.kt` | What a playlist can be: a folder, or one added in the tab — a single picture or video (`FilePlaylist`) or a link (`LinkPlaylist`, a web page or a stream, its kind guessed from the address) |
| `SignageStore.kt` | The tab's settings (`signage.json`, the added playlists among them) and the hash cache (`hashes.json`) |
| `ManifestBuilder.kt` | One screen's `Manifest` and the file behind each hash; a link only for a player that listed its kind |
| `WebTestVerdict.kt` | What a screen's web page test says: smooth, normal motion only, or too slow — decided here, so the thresholds change without a player update |
| `ScreenControls.kt` | What the tab does to screens without the folder: pairing, rename, identify, wipe, previews, player updates |
| `SignageTab.kt`, `SignageScreenCards.kt`, `SignageFolderPanel.kt`, `SignageDialogs.kt`, `LinkDialog.kt`, `ScreenPreview.kt`, `ScreenUpdateLine.kt`, `WebTestLine.kt` | The tab: each screen's card with its live picture, update line and web page test, the folder panel with its Add menu, the dialogs. `SignageDesktop` (in `SignageTab.kt`) is the file manager, folder chooser and Trash it uses |

## Rules

- **Folder and store work happens on one IO thread** (`Dispatchers.IO.limitedParallelism(1)`), so
  it never overlaps itself and never touches the event thread.
- **A folder change is published once it has stood still for one more read**, so a copy in
  progress is not sent half-written. Changes made while ChurchPresenter was off are what the first
  reads find; the hash cache keeps that cheap.
- **Hiding the tab stops the controller, never the screens**: they keep playing what they have.
- **Wipe publishes first, then tells the screen**: the empty manifest goes out before the wipe, or
  the screen would fetch the old one straight back.
- **Previews are asked for only while the tab is showing**, every 5 s; a picture older than 20 s is
  not shown as current.
- **A link goes only to a player that listed its kind** (`stream`, `web`): an older player cannot
  decode one. What each screen can do is remembered after it disconnects (`KnownFeatures`), so a
  screen away for a moment is not sent a manifest without its links.
- **Removing an added playlist never touches a file**, and sends any screen that played it back to
  the first playlist.

## Tests

`SignageWorld` (`SignageTestSupport.kt`) is a folder, a hub and a controller in a temp directory the
test owns, with player releases served from memory and signed with a key made for the test. Waits
listen on the hub's own flows (`expect` starts listening before the action: messages to a screen are
not replayed). UI tests hand the tab a `FakeDesktop`, so nothing opens a real file manager, chooser
or Trash. `ScreenCardAloneTest` and `SignageFolderPanelAloneTest` compose one piece on its own and
change its inputs between frames, which is where most branches are; the main window's
`AccessibleNamesTest` and `FocusRingVisibleTest` are `SignageTabAccessibilityTest` here, since the
tab needs a running hub.

## Commands

```bash
./gradlew :signage:test :signage:detekt
```
