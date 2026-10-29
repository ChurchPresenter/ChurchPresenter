# `:signage` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Signage tab**: the lobby screens this ChurchPresenter owns, the players asking
to join, and the **playlists** they play — each added in the tab. It starts hidden, as STT does
(`AppSettings.hiddenTabs`, settings version 25), and signage runs while it is shown in the tab bar's
show/hide menu. A feature-layer module. It depends
on `:signage-core` for the wire types and the hub, and knows nothing of `:server`: `:composeApp`
gives the same `SignageHub` to both.

## Layout

| File | Holds |
|---|---|
| `SignageController.kt` | Keeps every paired screen's manifest in step with the playlists and the tab's choices; runs while the tab is shown in the tab bar, whichever tab is selected |
| `SignageFolder.kt` | Reads a folder playlist's copy: the pictures and videos directly in it, in name order |
| `SignageMedia.kt`, `AddedPlaylistEdits.kt` | Copying a picked folder or file into the signage folder (`Folders/`, `Files/`), finding a copy from its relative path, sending a removed one to the Trash; the tab's playlist edits over them |
| `PlaylistSource.kt` | What a playlist can be, each added in the tab: a folder (`PickedFolderPlaylist`), a single picture or video (`FilePlaylist`), a link (`LinkPlaylist`, a web page or a stream, its kind guessed from the address), or a rotation (`RotationPlaylist`: other entries in turn, each for its own time) |
| `SignageStore.kt` | The tab's settings (`signage.json`: the added playlists, and each screen's plan with its weekly schedule) in the signage folder, and the hash cache (`hashes.json`) in this computer's own |
| `ManifestBuilder.kt` | One screen's `Manifest` and the file behind each hash; a link only for a player that listed its kind |
| `WebTestVerdict.kt` | What a screen's web page test says: smooth, normal motion only, or too slow — decided here, so the thresholds change without a player update |
| `ScreenControls.kt` | What the tab does to screens without the folder: pairing, rename, identify, wipe, previews, updates (`ScreenPower`: restart, shut down, the TV) |
| `ScreenSettingsDialog.kt`, `ScreenSettingsEdit.kt`, `ScreenSettingsWiring.kt` | A screen's settings: name and mounting, the touchscreen (corner gesture, which touch page, PIN), and Device (address, details, health, TV, restart, shut down, factory reset); what it opens on and saves |
| `SignageTab.kt`, `SignageScreenCards.kt`, `SignageFolderPanel.kt`, `SignageHelp.kt`, `SignageDialogs.kt`, `LinkDialog.kt`, `RotationDialog.kt`, `ScheduleDialog.kt`, `ScreenPreview.kt`, `ScreenUpdateLine.kt`, `ScreenHealthLine.kt`, `WebTestLine.kt` | The tab: each screen's card with its live picture, what it shows, its update line and button, schedule and web page test (its details and health are in its settings; the card shows health only under strain), the playlist panel with its Help and Add menu, the dialogs. `SignageDesktop` (in `SignageTab.kt`) is the file and folder chooser it uses |

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
- **A touch page goes only to a player that lists `interactive`**, as a link only goes to one that
  lists its kind; a rotation stops on it. A PIN is kept and sent only as `KioskPin.hash`, made on
  the store's IO thread (it is slow on purpose); set, changed and removed here, never on the screen.
- **Restart, shut down, the TV and Update's system step go only to a player that lists `power`,
  `tv` and `system_update`.** Update sends `UpdateSystem` when no newer player is offered.
- **The tab uses the app's own controls** (`:theme`'s `RaisedButton`, `KeyButton`, `SettingsTextField`;
  `RingedGhostButton` here for the quiet actions, which draws the focus ring `GhostButton` does not).
  A settings field draws its caption apart from its box: tests find the box by its tag (`field(tag)`).
- **The signage folder holds everything**, so every computer sharing it plays the same content: a
  folder or a picture or video added in the tab is copied in (`Folders/<name>`, `Files/<name>`,
  made under a hidden name and moved into place, so a computer reading the share never sees half a
  copy) and kept by its path relative to the signage folder. One picked from inside the signage
  folder already (by real path) is not copied but played where it is, so whoever drops pictures
  into a shared folder there updates the screens. A path saved absolute is played where it is, on
  that computer only. Copies run off the controller's thread.
- **Removing a playlist removes its copy (to the Trash where there is one), never the original**,
  never a copy another playlist still plays, and never anything but a `Folders/<name>` or
  `Files/<name>` the tab made — a folder played where it is stays. It sends any screen that
  played it back to the first playlist, leaves every rotation, and keeps a screen's schedule.
- **The setup may be shared.** The signage folder (`AppSettings.signageStorageDirectory`, set in
  System settings, blank for the app data folder's `signage`) holds `screens.json` and
  `signage.json` with the copies under `Folders` and `Files` (moving to a new folder brings them
  along); any computer pointed at
  it runs the same screens. What another computer writes there is read on the next poll, by content
  not time (a share keeps times only to a second or two). This computer's caches (`hashes.json`,
  player builds) stay in its app data folder.

## Tests

`SignageWorld` (`SignageTestSupport.kt`) is a signage folder, a hub and a controller in a temp
directory the test owns, with a copied folder playlist already added for each of `folders`
(`world.file` writes into them, `world.original` outside, to add from),
and player releases served from memory and signed with a key made for the test. Waits listen on
the hub's own flows (`expect` starts listening before the action: messages to a screen are not
replayed). UI tests hand the tab a `FakeDesktop`, so nothing opens a real chooser, file manager or Trash.
`ScreenCardAloneTest` and `SignageFolderPanelAloneTest` compose one piece on its own and
change its inputs between frames, which is where most branches are; the main window's
`AccessibleNamesTest` and `FocusRingVisibleTest` are `SignageTabAccessibilityTest` here, since the
tab needs a running hub.

## Commands

```bash
./gradlew :signage:test :signage:detekt
```
