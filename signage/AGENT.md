# `:signage` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Signage tab** (dev mode only): the lobby screens this ChurchPresenter owns, the players asking
to join, and the **signage folder** their playlists come from. A feature-layer module. It depends
on `:signage-core` for the wire types and the hub, and knows nothing of `:server`: `:composeApp`
gives the same `SignageHub` to both.

## Layout

| File | Holds |
|---|---|
| `SignageController.kt` | Keeps every paired screen's manifest in step with the folder and the tab's choices; runs while dev mode is on, whether or not the tab is showing |
| `SignageFolder.kt` | Reads the folder: its own files and each folder inside it are playlists |
| `SignageStore.kt` | The tab's settings (`signage.json`) and the hash cache (`hashes.json`) |
| `ManifestBuilder.kt` | One screen's `Manifest` and the file behind each hash |
| `ScreenControls.kt` | What the tab does to screens without the folder: pairing, rename, identify, wipe, previews, player updates |
| `SignageTab.kt`, `SignageScreenCards.kt`, `SignageFolderPanel.kt`, `SignageDialogs.kt`, `ScreenPreview.kt`, `ScreenUpdateLine.kt` | The tab: each screen's card with its live picture and update line, the folder panel, the dialogs. `SignageDesktop` (in `SignageTab.kt`) is the file manager, folder chooser and Trash it uses |

## Rules

- **Folder and store work happens on one IO thread** (`Dispatchers.IO.limitedParallelism(1)`), so
  it never overlaps itself and never touches the event thread.
- **A folder change is published once it has stood still for one more read**, so a copy in
  progress is not sent half-written. Changes made while ChurchPresenter was off are what the first
  reads find; the hash cache keeps that cheap.
- **Dev mode off stops the controller, never the screens**: they keep playing what they have.
- **Wipe publishes first, then tells the screen**: the empty manifest goes out before the wipe, or
  the screen would fetch the old one straight back.
- **Previews are asked for only while the tab is showing**, every 5 s; a picture older than 20 s is
  not shown as current.
- No screenshots while it is dev mode only (root `AGENT.md`, "Dev mode only").

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
