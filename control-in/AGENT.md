# `:control-in` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

MIDI and OSC, in and out (`docs/SHOW_CONTROL.md`, MIDI and OSC): the way a lighting desk, Ableton or
a stage controller drives the show, and the way the show drives them back.
- `Trigger` / `ControlEvent`: what arrived (a note, a control change, an MSC command, an OSC
  address) and the saved pattern that matches it. Both are flat, with a string `kind`, so a trigger
  from a newer build never fails the settings load; it simply never matches.
- `ControlMapping`: a trigger and the show-control actions it runs. `ControlOutput`: a show event
  (`OutputEvent`) and the MIDI or OSC message it sends. `ControlSettings`: the ports and both tables.
- `Osc` and `Midi`: plain functions that parse and encode bytes. No sockets, no devices.
- `OscServer`, `OscSender`, `MidiPorts`: the thin device layer over UDP and `javax.sound.midi`.
- `ControlHub`: opens the ports a `ControlSettings` names, turns what arrives into matches (or into
  a learned trigger), and sends the outputs.

A real Gradle module of this build: `include(":control-in")`. `:settings` stores `ControlSettings`
and `:composeApp` wires the hub to the show. It takes `:show-control` (a mapping runs actions),
kotlinx-serialization and coroutines, and nothing of `:composeApp`'s or `:settings`'.

## Package

**`org.churchpresenter.controlin`**.

## Rules

- **Parsing and matching are functions of bytes and values.** A device or socket call is one thin
  step that hands bytes to them, so the logic is tested headless.
- **A bad packet or message is dropped, never thrown** -- a desk sending garbage must not stop the
  listener.
- **Stored fields are additive and stringly typed** (`kind`, `on`); never rename one.
- **No Compose.**

## Commands

```bash
./gradlew :control-in:test :control-in:detekt
./gradlew :control-in:jacocoTestCoverageVerification
```
