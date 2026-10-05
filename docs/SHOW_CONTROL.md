# Design note: show control

Status: **draft, for review**. Phase 2 of `docs/PHASE2_PLAN.md`: the rest of roadmap stage 2
(messages, props, clear groups, `LiveShow` wired in) and stage 3 (cue actions, macros, MIDI/OSC).
It builds on `docs/LAYER_MODEL.md`, whose layers, cues and Preview/Take it assumes.

## Why

The layer model put every content type on its own layer, but nothing yet fills two of them and
nothing happens on screen unless someone clicks:

- **The Messages layer is empty.** `Cue.Message` exists and `LiveShow` already clears the other
  layers for it, but no tab, API or renderer puts one up (`CueContent` draws it as nothing).
- **There are no props.** A logo bug, a clock or a live badge has nowhere to live; the clock and
  countdowns exist only as announcement modes, which replace the slide.
- **Clearing is per layer or everything.** There is no "clear the text but keep the video".
- **Automation is a patchwork.** The Calendar's cue engine (`CueItem`, `CueAction`, `RowTiming`,
  `CueRunner`) runs a handful of actions on a schedule, keyboard shortcuts are enum branches in
  `MainDesktopKeys.kt`, remote commands are flows in `CompanionServer`, and each calls its own
  lambda. Two declared actions (`OBS_SCENE`, `ATEM_KEY`) do nothing. There is no MIDI or OSC.
- **`LiveShow` is not wired in.** `PresenterManager.program` is still derived through
  `legacyProgram`, so anything that wants to drive layers directly has to go through
  `setPresentingMode`.

## The model

### LiveShow as the source of program

`PresenterManager` keeps one `LiveShow`. Every content setter that puts something on air, and
every clear, goes through `LiveShow.set`/`clear`/`clearAll`; `program` is `LiveShow.program`. The
`Live*` parts stay as they are, as the state behind each cue (`docs/LAYER_MODEL.md`, "Live
state"). Nothing on screen changes; it is the prerequisite for every action below, which all speak
in layers.

The Preview bus stays a second `PresenterManager` in this phase. Moving it onto `LiveShow.preview`
is deferred until something needs it.

### Messages

```kotlin
data class Message(
    val text: String,               // already resolved: tokens filled in
    val template: String? = null,   // the saved template it came from, for the panel and the API
    val durationSeconds: Int? = null,
) : Cue  // layer = MESSAGES
```

- **Going live clears every other layer, then the message goes up alone** (decision 7 of the
  layer note; `LiveShow.goingLive` already does this).
- **It comes down** when its duration runs out, on Clear Messages or Clear All, or when slide
  content goes live -- as a full-screen announcement does today.
- **Templates** are saved in settings: a name, text with `{tokens}`, an optional duration, e.g.
  `Nursery` = "Parent of child #{number}, please come to the nursery". The panel asks for each
  token; the API takes them as fields.
- **Looks** gain `look.messages` (additive, on by default), so a stream key can leave them out.
- **Style**: drawn with the output's announcement look for now -- a message is a short
  announcement. Its own style category waits for themes (roadmap stage 4).
- **Instance Link** gains a `messages` layer to follow.

### Props

Persistent overlays that sit above the slide and the lower third and survive both going live:

| Kind | What it draws |
|---|---|
| Image | A logo bug or any picture, at a position and size |
| Clock | The time, in the announcements' clock format |
| Countdown badge | A small countdown to a time or for a duration, from the announcement timer modes |
| Live badge | A "LIVE" tag, for a stream key |

- **A layer of their own, `PROPS`, between Graphics and Announcements**, holding one
  `Cue.Props(on: Set<String>)` -- the ids of the props that are up. One cue per layer stays true,
  and a lower third coming and going never touches the logo bug.
- Prop definitions (kind, position, size, image, format) are saved in settings; switching one on or
  off is a live action, not a settings change.
- Clear All takes props down unless the clear group says otherwise (below); a message, going up
  alone, takes them down too.
- `look.props` (additive) lets an output leave them out.

### Clear groups

A clear group is a named set of layers cleared together: built in are **Clear All** (everything but
the background) and one per layer; a church adds its own, e.g. *Clear text* = Slide + Messages,
*Clear graphics* = Graphics + Props. Saved in settings, reachable from a button, a key, the API and
an action.

## Actions

One vocabulary for everything that changes the show, used by cue actions, macros, keys, the
HTTP/WebSocket API, Companion and MIDI/OSC:

| Action | Arguments | Does |
|---|---|---|
| `set` | a schedule row or item | Puts it on air (`executeProjectItem`) |
| `preview` | a schedule row or item | Cues it on Preview |
| `take` | optional layer | Take |
| `clear` | layer | Clears one layer |
| `clearGroup` | group | Clears a clear group |
| `message` | template, tokens, duration | Puts a message up |
| `prop` | prop, on/off/toggle | Switches a prop |
| `lowerThird` | preset | Runs a lower third |
| `timer` | timer mode, target | Starts an announcement countdown or count-up |
| `media` | play / pause / stop | Controls the playing media |
| `obsScene` | scene | Switches OBS's program scene |
| `atemKey` | key, on/off | Puts an ATEM keyer on or off air |
| `atemMacro` | macro index | Runs an ATEM macro (a new `:atem` command) |
| `companion` | surface, button | Presses a button on a Companion Satellite surface |
| `next` / `previous` | -- | Goes live with the next or previous schedule row |
| `wait` | seconds | Waits before the next action in the list |
| `macro` | name | Runs a macro |

- **A new module, `:show-control`**: the `Action` type, the list runner and the trigger mappings,
  with no Compose and nothing of the app's -- the app supplies one `ShowHost` interface of the calls
  above, built from what already exists (`CalendarHost`/`fireCue`, `ScheduleActions`,
  `OBSWebSocketManager.setScene`, `AtemClient.setKeyOnAir`, `CompanionSatelliteViewModel
  .pressButton`, `LowerThirdSequencer.run`, the announcement timer, `MediaViewModel`).
- **Serialized with an explicit `@SerialName` on every action**, never a class name, so the module
  can move without orphaning anyone's schedule (the rule in `settings/AGENT.md`). Unknown actions in
  a newer file are kept and skipped, as `CueAction` strings are today.
- **A list runs in order**, one coroutine per run; `wait` suspends it. A run is cancelled when its
  row fires again, or on Clear All.
- **The Calendar's cue sheet keeps working**: `CueAction`'s strings (`project`, `countdown`,
  `goLive`, `scene`, `blank`) become actions, and `obsScene`/`atemKey` start doing what they say.

### Cue actions

Each schedule row can carry a list of actions that run **when the row reaches the air**:

- Stored beside the rows in the `.schedule` file, keyed by row id, like `notes` and `timing`
  (`ScheduleFileV2.actions`). Older files read unchanged; older builds ignore the field.
- **With preview mode on they run on Take**, through the same deferral the Preview bus already
  uses for statistics (`PreviewBus.onAir`), so cueing a row never switches OBS.
- Stepping within the item on air (the next verse, the next section) does not fire them again.
- `CueRunner`'s rule still holds: when the operator has taken over, an automatic run yields.

### Macros

A macro is a named action list in settings, run from:

- a **Macros panel** of buttons;
- a **key**: any macro can be bound in the shortcut settings, beside the built-in actions;
- **HTTP** `POST /api/macro/{name}` and the WebSocket `macro` command;
- **Companion**, as actions and button feedback;
- **MIDI and OSC** (below).

A macro may run another; nesting deeper than 8 stops with a warning rather than looping.

### MIDI and OSC

A new module, `:control-in`:

- **MIDI in** through `javax.sound.midi` (in the JDK): notes, control changes and MIDI Show Control.
- **OSC in** over UDP, with a small codec of our own: OSC 1.0 messages and bundles are a few hundred
  lines, and nothing in the build reads OSC today.
- **A mapping table** in settings: a trigger (`note 60 on channel 1`, `/cp/macro/walk-in`) to an
  action or macro, with **Learn**: press the pad, pick the action.
- **Out**: the same table in reverse -- on go-live, take and clear, send a note or an OSC message,
  for lighting desks.
- The device and socket layers stay thin; parsing and mapping are plain functions, tested headless.

## Output isolation

Every output window composes on the operator UI's event thread and shares its Compose snapshot
state, and the off-screen outputs (`ComposeScenePump`) confine themselves to that thread on
purpose, to avoid a snapshot-observer lock inversion. So a hung operator UI freezes every output,
and a second render thread inside the same process cannot share that state safely.

This phase does three things, in order:

1. **A watchdog**: in a debug build, a frame of the event thread over budget is logged with its
   stack (`Log.warn`, so it reaches the crash report's breadcrumbs). The thread-dump code from the
   test suite's hung-test reporter moves into `:diagnostics` to serve both.
2. **The output paths stop touching the disk while composing** -- the file checks in the background,
   song background, looping video, lower-third cache and follower media paths.
3. **A spike on an output process**: the program, as plain cues plus the state behind them, sent to
   a separate process that draws the outputs. The decision -- build it, or stop at the watchdog -- is
   recorded here after the spike, with the cost measured.

## Migration

Each step shippable on its own, each keeping every existing test green:

1. **`LiveShow` wired in** (no visible change).
2. **Messages**: cue, renderer, templates, look switch, API, Instance Link layer, panel.
3. **Props**: the `PROPS` layer, definitions, renderer, panel, API.
4. **Clear groups**.
5. **`:show-control`**: actions, runner and host; the cue sheet's actions mapped onto it, OBS and
   ATEM made real.
6. **Cue actions** on schedule rows, and their editor.
7. **Macros**: panel, keys, API, Companion.
8. **MIDI and OSC** in and out (`:control-in`).
9. **Watchdog, output-path I/O, isolation spike.**

Steps 1–3 and 9 must show no regression on the render benchmark and the soak test.

## Decisions (proposed -- confirm or change at review)

1. **Messages** are their own content type on the Messages layer, drawn with the announcement look
   until themes; slide content going live takes them down.
2. **Props** get a layer of their own (`PROPS`, above Graphics), so a logo bug and a lower third are
   up at once.
3. **Clear groups** are user-defined sets of layers; Clear All keeps the background.
4. **One action vocabulary** in `:show-control`, serialized by explicit names; the Calendar's
   `CueAction` strings map onto it.
5. **Cue actions run on air**, so on Take in preview mode, and not again on stepping.
6. **OSC** with a codec of our own rather than a library; MIDI through the JDK.
7. **Output isolation** starts with the watchdog and the I/O fixes; an output process is decided after
   a spike.
