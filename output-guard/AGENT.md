# `:output-guard` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The output guard** (`docs/SHOW_CONTROL.md`, Output isolation): a second process of the app that
keeps a still of each output under its window and, if the app dies, shows it and starts the app
again — so a crash puts the last slide on the projector, not the desktop. **Dev mode only**, and off:
`ProjectionSettings.outputGuardEnabled`, in the System page's dev-mode card.

An engine-layer module, AWT only. The guard is the app's own executable started with
`--output-guard <folder>`: `main` branches into `runOutputGuard` before anything else, so the
packaged app needs no second launcher and the guard never loads Compose, Skiko or VLC.

## Layout

| Path | Owns |
|---|---|
| `GuardState.kt` | The memory-mapped file the two processes share: the app's pid, a heartbeat, a UI tick, the flags, and one slot per output (id, bounds, still number) |
| `GuardFiles.kt` | The folder (`~/.churchpresenter/output-guard/`): each output's still, the relaunch command, the live state a relaunched app puts back — every file written whole and moved into place |
| `GuardAction.kt` | `decide` and `successorTookOver`: the guard's choices as plain functions of what it sees |
| `OutputGuard.kt` | The guard process: `runOutputGuard`, `GuardProgram` (ten looks a second), `GuardWindows` (the backstop windows) |
| `OutputGuardLink.kt` | The app's side: starts and restarts the guard, beats the heartbeat and the UI tick, reports the outputs and their stills, and `stop()` for a quit on purpose |

`:composeApp`'s `OutputGuardWiring.kt` runs the link while dev mode and the setting allow, registers
each output window, saves a still and the live state a moment after anything on air changes, and puts
that state back in an app the guard relaunched (`--relaunched-by-output-guard`).

## Rules

- **Nothing here loads Compose, Skiko or VLC.** The guard starts in a fraction of the app's time and
  memory, and lives through what kills the app.
- **The guard's windows sit at normal level under the outputs, which are always on top**, so a guard
  window can never cover a live output. Covering raises them to the same level and in front.
- **A quit on purpose sets `closing`**: switching the guard off, the app quitting, or a shutdown hook.
  Only a crash or a kill leaves the guard to cover.
- **Never write a file in place.** The other process reads it at any moment.
- **What the tests cannot reach, and why**: the `JWindow` behind `Backstop` (`AwtBackstop`), the
  process entry (`runOutputGuard`, `startProcess`, the default exit) — a
  headless test can build neither a window nor a second process. Everything they decide is tested
  through `GuardWindows`, `GuardProgram`, `appToWatch` and the key-based `register`, with fakes.
  The behaviour itself was checked on macOS with a real `kill -9` (`docs/SHOW_CONTROL.md`).

## Commands

```bash
./gradlew :output-guard:test :output-guard:detekt
```
