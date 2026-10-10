# MIDI and OSC control (`:control-in`)

**Dev mode only**, like the rest of show control (`docs/SHOW_CONTROL.md`). Outside dev mode every
port stays closed -- the app hands the hub an empty `ControlSettings` (`ControlWiring.kt`), so saved
mappings and outputs do nothing until dev mode is on again. The **MIDI & OSC** dialog is opened
from the Macros dialog, whose button sits in the preview sidebar's Dev mode only box.

What this covers: how a lighting desk, Ableton or a stage controller drives the show over MIDI or
OSC, and how the show sends MIDI or OSC back out. Source: `control-in/src/main/kotlin/org/
churchpresenter/controlin/` -- `Trigger.kt`, `ControlSettings.kt`, `Midi.kt`, `Osc.kt`,
`Devices.kt`, `ControlHub.kt`. The settings live in `AppSettings.control`.

## Ports

| Field | Means | Off when |
|---|---|---|
| `midiInput` | The MIDI input device, by its system name (hardware only -- not a software sequencer or synthesizer) | blank |
| `midiOutput` | The MIDI output device, by its system name | blank |
| `oscInPort` | The UDP port OSC is received on, on every interface | `0` |
| `oscOutHost` / `oscOutPort` | Where OSC is sent, over UDP | host blank or port `0` |

Each port shows as off, open or failed (it was named but would not open: the device is gone, the
port is taken). Saving a mapping does not reopen a port; only a port whose own setting changed is
closed and opened again.

## Triggers

A mapping listens for one trigger. Each trigger has a `kind`, stored by these names:

| `kind` | What arrives | Fields that match |
|---|---|---|
| `midiNote` | A MIDI note on (velocity above 0) | `channel`, `number` (the note) |
| `midiCc` | A MIDI control change | `channel`, `number` (the controller), `value` |
| `msc` | A MIDI Show Control SysEx command | `address` (the command), `cue` |
| `osc` | An OSC message (also each message inside a bundle) | `address` (the path), `value` |

A `kind` the build does not know -- one written by a newer build -- never matches; it does not fail
the settings load.

### Matching rules

- **Channel** counts from 1. A trigger `channel` of `0` matches any channel. MSC and OSC ignore it.
- **Notes** match on channel and note number only; the velocity is not compared. A note on with
  velocity 0 is a note off and is ignored, as are note offs, clock and every other message.
- **Control changes** match on channel and controller number, and on `value` unless it is `-1`
  (any). `-1` is the default, so a mapping fires on every move of the fader unless a value is set.
- **MSC** matches the command name case-insensitively, and the `cue` exactly; a blank `cue` matches
  every cue (or none).
- **OSC** matches the address exactly (no wildcards), and `value` unless it is `-1`. The value an
  OSC message is matched on is its **first numeric argument**, truncated to an integer (a float
  `0.9` is `0`); a message with no numeric argument has value `-1`, so it matches only a trigger
  whose `value` is `-1`.
- **Every mapping that matches runs**, in table order -- two mappings on the same pad both fire.
- A malformed MIDI message or OSC packet is dropped, never thrown.

**OSC addresses are user-learned.** The app defines no OSC address space of its own: there is no
built-in `/cp/...` path. Whatever address a desk sends is the address the mapping matches, set by
Learn or typed in.

### Learn

Press **Learn**, then press the pad, move the fader or fire the cue on the device: the next thing
that arrives on an open input becomes the trigger instead of running any mapping. What is saved:

| Arrived | Saved as |
|---|---|
| Note on | `midiNote` with its channel and note; velocity not kept |
| Control change | `midiCc` with its channel and controller; `value` `-1` (any), so learning a fader learns the fader, not the position it was in |
| MSC command | `msc` with its command and cue |
| OSC message | `osc` with its address; `value` `-1` (any) |

Learn waits until something arrives or it is cancelled. Narrow a learned trigger by editing it
afterwards -- set a `value`, or set `channel` to `0` to take any channel.

### MIDI Show Control

An MSC message is `F0 7F <device> 02 <format> <command> [cue ...] F7`. The device id and command
format are not checked. The cue is the ASCII bytes after the command, up to the first `00` (the cue
list and path that may follow are not kept). Supported commands, by their stored name:

| Byte | `address` |
|---|---|
| `01` | `GO` |
| `02` | `STOP` |
| `03` | `RESUME` |
| `04` | `TIMED_GO` |
| `05` | `LOAD` |
| `06` | `SET` |
| `07` | `FIRE` |
| `08` | `ALL_OFF` |
| `09` | `RESTORE` |
| `0A` | `RESET` |
| `0B` | `GO_OFF` |

Any other command byte, and any other SysEx, is ignored.

### OSC

OSC 1.0 over UDP, with a codec of the app's own. Messages and bundles (`#bundle`, nested bundles
unpacked; the time tag is ignored -- everything runs on arrival) are read. Argument types read:
`i`, `f`, `h`, `d`, `s`, `b`, `T`, `F`, `N`, `I`. A message with no type-tag string is read as having
no arguments.

## Actions

A mapping runs a list of show-control actions, in order, exactly as a macro does (one run per
press, through the app's `ActionRunner`). Each action is a JSON object whose `type` is its serial
name; the other fields are its arguments. Fields an action does not have are ignored, and a `type`
this build does not know is kept and skipped.

| `type` | Fields | Does |
|---|---|---|
| `set` | `rowId`, or `item` (a schedule item); `plays` (default 1, 0 loops) | Puts a schedule row, or the item carried, on air |
| `preview` | `rowId`, or `item` | Cues it on Preview |
| `take` | `layer` (blank = every layer) | Takes what is cued to air |
| `clear` | `layer` (e.g. `SLIDE`, `MESSAGES`) | Takes one layer off air |
| `clearAll` | -- | Takes everything off air, as the Clear button does |
| `clearGroup` | `group` (id or name) | Clears an operator's clear group |
| `message` | `text`, or `template` with `tokens`; `durationSeconds` | Puts a message up |
| `prop` | `prop` (id or name), `on` (`true`, `false`, or absent to toggle) | Switches a prop |
| `lowerThird` | `preset` (name) | Runs a lower-third preset |
| `timer` | `mode` (`duration`, `clock`, `count_up`), `seconds`, `until` (`HH:mm`) | Starts an announcement timer |
| `media` | `command` (`play`, `pause`, `stop`) | Controls the loaded media |
| `obsScene` | `scene` | Switches OBS's program scene |
| `atemKey` | `downstream`, `mixEffect`, `keyer` (from 0), `on` | Puts an ATEM keyer on or off air |
| `atemMacro` | `index` (from 0) | Runs an ATEM macro |
| `companion` | `connection`, `button`, `placement` (`TAB`, `LEFT_SIDEBAR`, `RIGHT_SIDEBAR`, or blank for any) | Presses a button on a Companion Satellite surface |
| `next` | -- | Goes live with the next schedule row |
| `previous` | -- | Goes live with the previous schedule row |
| `wait` | `seconds` (decimal) | Waits before the next action |
| `macro` | `name` | Runs a macro |

Layers are named as the live show spells them (`SLIDE`, `MESSAGES`, ...). See `Action.kt` in
`:show-control` and `docs/SHOW_CONTROL.md`, Actions.

## Outputs

The same table in reverse: when the show does something, send a MIDI or OSC message -- for a
lighting desk to follow the service.

| `on` | Sent when |
|---|---|
| `goLive` | Something new goes on air (a content type that was not live before) |
| `take` | Take puts the Preview bus on air |
| `clear` | The last thing on air comes down, leaving nothing live |

What is sent (`message`, an `OutMessage`):

| `kind` | Sends | Fields |
|---|---|---|
| `midiNote` | A note on at velocity `value` (1--127) then its note off, on `midiOutput` | `channel` (1--16), `number`, `value` |
| `midiCc` | One control change, on `midiOutput` | `channel`, `number`, `value` (0--127) |
| `osc` | One OSC message to `oscOutHost`:`oscOutPort` | `address`, `argument` -- sent as an int if it reads as one, else a float, else a string; blank sends no argument |

Out-of-range MIDI numbers are clamped. A send to a port that is not open, or that fails, is
dropped. `msc` is not an output kind; nothing is sent for it.

## Example

One mapping and one output, as they are stored in `AppSettings.control`:

```json
{
  "midiInput": "nanoKONTROL2",
  "oscInPort": 9000,
  "oscOutHost": "192.168.1.40",
  "oscOutPort": 8000,
  "mappings": [
    {
      "id": "map-walk-in",
      "name": "Walk in",
      "trigger": { "kind": "midiNote", "channel": 1, "number": 60, "value": -1, "address": "", "cue": "" },
      "actions": [
        { "type": "message", "template": "Welcome", "durationSeconds": 30 },
        { "type": "wait", "seconds": 2.0 },
        { "type": "macro", "name": "Walk in" }
      ]
    },
    {
      "id": "map-clear",
      "name": "Desk clear",
      "trigger": { "kind": "osc", "channel": 1, "number": 0, "value": -1, "address": "/desk/clear", "cue": "" },
      "actions": [ { "type": "clearAll" } ]
    }
  ],
  "outputs": [
    {
      "id": "out-live",
      "on": "goLive",
      "message": { "kind": "osc", "channel": 1, "number": 0, "value": 127, "address": "/eos/macro/5/fire", "argument": "" }
    }
  ]
}
```

Fields left at their defaults may be omitted: a trigger's `channel` is `1`, `number` `0`, `value`
`-1`, `address` and `cue` blank; an output's `message.kind` is `midiNote` with `value` `127`.
