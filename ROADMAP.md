# Roadmap — from feature-rich to top-tier

What it would take to turn ChurchPresenter into a top-tier presentation production application:
one a production team picks over ProPresenter 7, EasyWorship 7 or FreeShow because it is *better
at running a live show*, not because it is free.

## Where it stands

The feature list is already wider than most of the field (`FEATURES.md`): songs with chords and
two languages, Bibles with speech-driven follow-along, PowerPoint/Keynote with real animations,
Lottie lower thirds, a scene compositor, captions and translation, Q&A, output profiles, NDI/OMT/
DeckLink/Browser Source out, ATEM/OBS/Companion control, Instance Link, Calendar and Planning
Center import.

What separates it from a top-tier tool is not more features. It is four things the field leaders
have and this app does not yet:

1. **One show model.** Songs, Bibles, slides, media and graphics each have their own tab, look
   settings and live path. A top-tier tool treats them all as cues on layers, styled by one theme
   system and run by one transport.
2. **Show control.** No preview-then-take, no per-layer clear, no cue actions, no MIDI/OSC/
   timecode. Everything that happens on screen still happens because someone clicked it.
3. **A production-grade media and output engine,** measured against a frame budget. Today there
   is no performance gate in CI and no output mapping (edge blending, LED walls).
4. **The way in.** Teams switching from ProPresenter or EasyWorship bring years of content.
   Without a faithful import of their files, the switch never starts.

## Phase 0 — Foundations (prerequisite for everything below)

| Work | Done when |
|---|---|
| **Finish modularization**: settings dialog tabs, `server/` | Each feature owns its settings page and routes; `:composeApp` is the shell, the wiring and the output pipeline |
| **Render performance budget** | A benchmark suite renders each content type to an off-screen output and fails CI past a frame-time budget (e.g. p99 < 16.7 ms at 1080p, < 33 ms at 4K) |
| **Live-safe threading** | No disk, network or decode work on the UI thread during a show, enforced by a debug-build watchdog that logs any frame over budget with its stack |
| **Output isolation** | An output window survives a crash or hang of the operator UI (separate render thread at minimum; evaluate a separate process) |
| **Soak test** | A scripted four-hour service runs nightly with memory and frame-time charts; a leak or a stall fails it |
| **Release engineering** | Signed and notarized installers on all three platforms, delta auto-update, beta and stable channels, crash-free-session rate tracked through the existing Sentry bridge |

## Phase 1 — One show model: layers, preview/program, clear groups

The core of "production-grade". Everything later builds on it.

- **Layers.** A fixed stack every output composes: Audio · Background/Video input · Media ·
  Slide (songs, Bible, presentations) · Graphics (lower thirds, props) · Messages · Announcements.
  Each layer has its own transition and its own Clear; Clear All and user-defined clear groups
  follow. `PresenterManager`'s per-content parts become per-layer state.
- **Preview / Program.** Cue the next thing into Preview, see it on the operator screen, and Take
  it with a transition, as a vision mixer does. Keep today's direct Go Live as a preference.
- **Looks.** An output profile decides which layers it shows. The stage monitor drops Background;
  the stream key drops everything but Slide and Graphics. Today's profiles are halfway there.
- **Messages.** Short live text on its own layer that never displaces the slide, e.g. "Parent of
  child #42 to the nursery", with templates and tokens.
- **Props.** Persistent overlays (logo bug, clock, live/countdown badge) on the Graphics layer.

Done when: any cue from any tab lands on its layer, Preview/Take works for every content type,
and clearing one layer never disturbs another. Verified by screenshot suites per layer combination.

## Phase 2 — Slides and themes: one design system for everything

- **Slide editor.** A WYSIWYG canvas for free-form slides: text boxes, shapes, images, video,
  live elements (clock, countdown, captions, NDI). The scene compositor and the profile text boxes
  already hold most of the pieces; this unifies them.
- **Themes.** A theme is a set of slide masters. Songs and Bible verses are *rendered through* a
  theme rather than styled by profile pages, so one change restyles every song, and a song or
  service can override it. Output profiles keep only output concerns (size, scaling, layers).
- **Song arrangements.** Named section orders per song ("Sunday", "Acoustic", "Christmas"), picked
  per schedule item.
- **Reflow.** Edit lyrics and the slides re-split under the theme's rules (lines per slide, fit).

Done when: a new church can style every content type from one theme without opening a settings
page, and the existing profile settings migrate into themes losslessly.

## Phase 3 — Show control and automation

- **Cue actions.** Any slide or schedule item can fire actions when it goes live: play media,
  start a timer, show a lower third or prop, switch an OBS scene, run an ATEM macro, press a
  Companion button, clear a layer, wait N seconds, go to the next cue.
- **Macros.** Named action lists on a button, a key or a remote command.
- **Control in and out.** MIDI (notes/CC/MSC), OSC and a documented TCP/HTTP API, all mapped to
  the same action set. This is what lets lighting desks, Ableton and Companion drive the show.
- **Timelines.** A schedule item with timed cues recorded against an audio track or timecode
  (LTC/MTC chase), so lyrics follow a click track or backing tracks without an operator.

Done when: a full service can run hands-off from a timeline, and every action is reachable from
MIDI, OSC, HTTP and Companion.

## Phase 4 — Media and output engine

- **Media bins and playlists** with thumbnails, in/out points, loop modes and per-item transitions.
- **Audio.** An audio bin and playlist with fades, crossfades, ducking under video, and routing to
  devices and channels per layer. Pre-roll and countdown music without leaving the slide.
- **Playback.** GPU decode where available, HAP and ProRes (with alpha) for motion backgrounds,
  frame-accurate cueing, seamless loops.
- **Screen configuration.** A canvas editor that maps outputs to physical displays: edge blending
  for projector arrays, LED wall slicing and pixel mapping, masks, keystone, color correction per
  output.
- **Sync.** Outputs presented on the same vsync; latency measured and shown per output.

Done when: a 4K motion background, a lyric slide and a lower third run across three blended
projectors and an NDI feed inside the Phase 0 frame budget.

## Phase 5 — The way in, and working together

- **Import.** ProPresenter 6/7 (`.pro`, playlists, libraries), EasyWorship 7 and MediaShout files,
  with a report of what could not be carried across. Extend `:converter`, which already reads
  seven song formats.
- **Licensing services.** CCLI SongSelect import and usage reporting, which needs a CCLI
  partnership. Planning Center Live control (follow the plan's live position), beyond today's
  import.
- **Shared library.** Songs, media and themes synced across a church's machines and campuses,
  built on the end-to-end-encrypted relay the Calendar already uses.
- **Multi-operator.** Roles (operator, editor, viewer), a live lock on the show, and a remote
  operator surface on a tablet that is a real second seat, not a remote control.

## Throughout — the polish that reads as "pro"

- **Operator UX.** A command palette, a dockable workspace saved per operator, every live action
  one key away. Measure clicks-to-live for common moves and drive them down.
- **Consistency.** One design system across every window, tested by the screenshot suites.
- **Accessibility.** Keyboard reachability and screen-reader labels on every control.
- **Documentation.** A docs site with task-based guides and short videos, built from the same
  screenshots CI already renders.
- **Extensibility.** A plugin API over the action set and layers, so integrations stop needing
  to be in-tree.

## Execution plan, in order

Steps are ordered by what each one needs. Within a stage, the lanes run in parallel; a stage starts
when the stage before it is done, except where a step says otherwise. **Nothing touches the live
output path (`PresenterManager`, `presenter/`, the output-profile pages, `server/`'s live
commands) until the layer design is approved (step 1.1)**, so no code is moved twice.

### Stage 1 — Decide and measure (no output-path code changes)

| # | Lane | Step | Needs | Done when |
|---|---|---|---|---|
| 1.1 | Design | **Layer-model design note**: the layer list, which content goes on which layer, per-layer state replacing `PresenterManager`'s per-content parts, Preview/Take, how output profiles become looks, the command shapes remote control and Companion will use | — | Reviewed and approved |
| 1.2 | Quality | **Render benchmark**: every content type rendered off-screen at 1080p and 4K, frame times recorded | — | Today's numbers committed as the baseline |
| 1.3 | Quality | **Soak test**: a scripted four-hour service, run nightly, charting memory and frame time | 1.2 | Runs green on `main` |
| 1.4 | Modules | **Extract the settings tabs that are not about outputs**: System, Server, ATEM, OBS, Companion, Calendar sync, Planning Center | — | Each in its feature's module or a settings-UI module; app check green |
| 1.5 | Import | **Format survey**: ProPresenter 6/7 (`.pro`) and EasyWorship, mapped onto today's song, slide and schedule models | — | Mapping written down, gaps listed |
| 1.6 | Release | **Signed and notarized installers** on all three platforms | — | CI produces them on every release |

### Stage 2 — Build the show model

| # | Lane | Step | Needs | Done when |
|---|---|---|---|---|
| 2.1 | Core | **Layers**: per-layer state and composition in every output; each layer with its own transition and Clear; Clear All | 1.1, 1.2 | Every content type lands on its layer; benchmark no slower than the 1.2 baseline |
| 2.2 | Core | **Preview / Program** with Take and transitions; direct Go Live kept as a preference | 2.1 | Works for every content type; screenshot suites per layer combination |
| 2.3 | Modules | **Extract `server/`** against the new layer commands | 1.1 (can start alongside 2.1) | Routes in their feature modules; remote, Companion and Instance Link tests green |
| 2.4 | Core | **Looks**: output profiles choose which layers they show | 2.1 | Stage monitor and stream key configured as looks |
| 2.5 | Core | **Messages and props** on their layers | 2.1 | Nursery message and logo bug run over a live song without disturbing it |
| 2.6 | Import | **ProPresenter import** in `:converter` | 1.5 | A real library imports with a report of what did not carry over |
| 2.7 | Release | **Delta auto-update, beta and stable channels, crash-free-session tracking** | 1.6 | A beta build updates itself |
| 2.8 | Quality | **Live-safe threading watchdog** and **output isolation** from the operator UI | 2.1 | A forced UI hang leaves every output running |

### Stage 3 — Automate the show

| # | Lane | Step | Needs | Done when |
|---|---|---|---|---|
| 3.1 | Core | **Cue actions**: media, timers, lower thirds, props, OBS, ATEM, Companion, clear, wait, next | 2.1, 2.2 | Any schedule item or slide can fire a list of actions |
| 3.2 | Core | **Macros** on buttons, keys and remote commands | 3.1 | Macros callable from every input |
| 3.3 | Control | **MIDI and OSC in and out**, plus a documented HTTP API, mapped to the action set | 3.1 (the input module can be built from stage 1 on) | A lighting desk and Ableton can drive the show |
| 3.4 | Import | **EasyWorship import** | 2.6 | As 2.6 |

### Stage 4 — One design system

| # | Lane | Step | Needs | Done when |
|---|---|---|---|---|
| 4.1 | Core | **Slide editor**: free-form text, shapes, media and live elements, reusing the scene compositor | 2.1 | A presentation can be built in-app |
| 4.2 | Core | **Themes**: slide masters that songs and Bible render through; overrides per song and service | 4.1 | Every content type styled from one theme |
| 4.3 | Modules | **Migrate the output-profile style pages into themes**; what is left (size, scaling, layers) becomes the looks editor | 4.2, 2.4 | Existing settings migrate losslessly; the old profile style pages are gone |
| 4.4 | Core | **Song arrangements and reflow** | 4.2 | Named section orders per schedule item; edits re-split under the theme |

### Stage 5 — Timelines, media and outputs

| # | Lane | Step | Needs | Done when |
|---|---|---|---|---|
| 5.1 | Core | **Timelines** with LTC/MTC chase and audio-synced cue recording | 3.1 | A service runs hands-off from a timeline |
| 5.2 | Media | **Media and audio bins and playlists**, fades, ducking, routing per layer | 2.1 | Pre-service music and video run from bins |
| 5.3 | Media | **GPU decode, HAP and ProRes with alpha**, frame-accurate cueing | 5.2 | A 4K motion background inside the frame budget |
| 5.4 | Output | **Screen configuration**: edge blending, LED wall slicing, masks, keystone, per-output colour; synced presentation | 2.4 | Three blended projectors and an NDI feed inside the frame budget |

### Stage 6 — Ecosystem and teams

| # | Lane | Step | Needs | Done when |
|---|---|---|---|---|
| 6.1 | Ecosystem | **CCLI SongSelect** (needs a CCLI partnership; start the conversation in stage 1) | — | Search and import from SongSelect |
| 6.2 | Ecosystem | **Planning Center Live** control | 2.2 | The schedule follows the plan's live position |
| 6.3 | Teams | **Shared library** across machines and campuses on the Calendar's encrypted relay | 4.2 | Songs, media and themes stay in sync |
| 6.4 | Teams | **Multi-operator** roles, live lock, a second-seat tablet surface | 2.3, 6.3 | Two operators run one show safely |
| 6.5 | Platform | **Plugin API** over actions and layers | 3.2 | An integration ships out of tree |

### Throughout every stage

Operator UX (command palette, dockable workspace, clicks-to-live measured), accessibility labels,
the docs site built from the CI screenshots, and the benchmark and soak test kept green.
