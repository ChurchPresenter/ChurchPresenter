# Phase 2 — finish the show model, then automate the show

## Context

Phase 1 (layer model, Preview/Take, looks, `presentingMode` retired) is merged to
`ChurchPresenter/ChurchPresenter` main. The roadmap's execution order (`ROADMAP.md`, formerly on
`claude/top-tier-roadmap`, stages 2–3) says what comes next. You chose that order over the
roadmap's "Phase 2 = slides & themes".

What is left:

- **The rest of stage 2.**
  - 2.5 Messages and props.
  - 2.8 Watchdog and output isolation.
  - The layer-model loose end: `LiveShow` is still not wired in. `PresenterManager.program` is
    derived through `legacyProgram`, and `Cue.Message` draws nothing.
- **Stage 3, "automate the show".**
  - 3.1 Cue actions.
  - 3.2 Macros.
  - 3.3 MIDI/OSC in and out.

Intended outcome:
- A nursery message and a logo bug run over a live song without disturbing it.
- Any schedule item can fire a list of actions when it goes live.
- Named macros are reachable from a button, a key, HTTP/WS, Companion and MIDI/OSC.
- A UI hang no longer freezes the outputs, or at least is caught and reported.

Parallel lanes that need nothing from this core work: 2.6 ProPresenter import (`:converter`), 3.4
EasyWorship import and 2.7 release work. They are listed at the end and are optional for this phase.

## Step 0 — Branch and design note

1. Fork sync (done): `zitlem/ChurchPresenter` `main` fast-forwarded to upstream `86784156`:
   - `git fetch https://github.com/ChurchPresenter/ChurchPresenter.git main`
   - `git push origin FETCH_HEAD:refs/heads/main`
   - Verify that main contains the phase-1 commits (`497c3f13` or its merge).
2. Branch `claude/phase2` cut from that main (done).
3. `ROADMAP.md` brought over from `claude/top-tier-roadmap` (done).
4. **`docs/SHOW_CONTROL.md`** (drafted, awaiting review), a design note in the style of `docs/LAYER_MODEL.md`, reviewed
   before any code. It covers:
   - Messages: the cue fields (text, template, tokens, duration), and the rule that a message going
     live clears every other layer and goes up alone (decision 7).
   - Props: where they live on Graphics, and how several props coexist with a lower third.
   - Clear groups.
   - The action vocabulary and its serialized names.
   - Cue actions per schedule row.
   - Macros.
   - Triggers: keys, HTTP/WS, Companion, MIDI, OSC.
   - What "output isolation" will mean, settled by the spike in step 9.

Each step below is its own commit or PR-sized chunk. Each keeps every suite green. A step that
touches the UI stops at "compiles, existing suites pass, show it" and waits for approval before
new tests or screenshots, per AGENT.md.

## Stage 2 leftovers

### Step 1 — The content setters write cues (no visible change)

`PresenterManager` becomes the façade over `LiveShow` that `LAYER_MODEL.md` describes.

- Construct one `LiveShow` in `PresenterManager`.
- `setPresentingMode`, `showOverlay`/`clearOverlay` (`viewmodel/LiveOverlays.kt`) and the clear path
  call `LiveShow.set`/`clear`.
- `program` becomes `LiveShow.program`, retiring the `legacyProgram` fold in
  `viewmodel/LegacyProgram.kt`. `legacyCue` stays as the builder of the cue from each part's state.
- `PresenterContext.slideMode` is kept only as long as a reader needs it.
- `OutputLayers` still builds a per-output program for locked or shown modes. It reads
  `legacyProgram` until the locks themselves become looks (later).
- Tests:
  - Existing `LiveShowTest` and `CueTest`.
  - The `PresenterManagerOverlayTest`/`PreviewBus*` suites stay green.
  - New mapping tests assert that `pm.program` equals `liveShow.program` after every setter.
- Benchmark: no regression.

### Step 2 — Messages (UI → approval)

- Cue and layer:
  - `Cue.Message(text, template?, durationSeconds?)` gets a `MessageCue` renderer in
    `presenter/CueTextContent.kt`, styled from the announcement look for now.
  - The `CueContent` branch replaces `is Cue.Message -> Unit`.
  - A `Presenting.MESSAGE` content type, or a dedicated message state in a new `LiveMessages` part,
    holds it. The design note picks one.
  - `LiveShow.goingLive` already clears the other layers for MESSAGES.
- Templates with tokens: a list of saved messages in settings, e.g. "Parent of child #{number} to
  the nursery". Additive field, no version bump.
- Duration: the message clears itself, and only the Messages layer clears.
- Look: `look.messages` switch in `settings/OutputLook.kt` (additive). `LEGACY_LOOK_PATHS` is
  untouched.
- Remote:
  - `POST /api/message` with text/template/tokens/duration, and a WS `message` command
    (`server/.../ScheduleRoutes.kt` `liveControlRoutes`, `WebSocketCommands.kt`).
  - `overlayForLayerName` gains `messages`, so `?layer=messages` clears it.
  - Documented in `COMPANION_API.md`.
- Instance Link: `LinkLayers.MESSAGES`, plus a `LiveContent.message` field
  (`LiveStateBroadcastWiring.kt`, `remote/RemoteLayers.kt`).
- UI: a Messages panel (template picker, token fields, Go Live/Clear) beside the preview sidebar.
  Strings go in `strings/.../values/strings.xml` only.

### Step 3 — Props (UI → approval)

- Props get a layer of their own, `PROPS` above Graphics, holding one `Cue.Props(on: Set<String>)`.
  A logo bug then survives a lower third. See `docs/SHOW_CONTROL.md`, decision 2.
- Kinds: logo/image bug, clock (reuse the announcement clock formatting in
  `viewmodel/LiveAnnouncements.kt`), live badge, countdown badge (reuse the announcement timer
  modes).
- Prop definitions (position, size, image, kind) live in settings. Toggling is a live action.
- `look.graphics` already gates outputs. A per-output prop filter is added only if the design note
  needs it.
- Remote: `/api/props/{id}/on|off`, WS, and a Companion feedback field.

### Step 4 — Clear groups (small; UI → approval)

- User-defined named sets of layers, e.g. "Clear text" = Slide + Messages.
- They clear through `LiveShow.clear` per layer.
- Clear All keeps the Background unless asked (`clearAll(keepBackground)`).
- Exposed as buttons, a shortcut (`ShortcutAction` dynamic entries) and `/api/clear?group=`.

## Stage 3 — Automate the show

### Step 5 — One action vocabulary (`:show-control` module, no UI)

- A serializable `sealed interface Action`. Every subtype gets an explicit `@SerialName`, per
  `settings/AGENT.md`'s rule on polymorphic types.
- Actions:
  - Layers: `Set(cue)`, `Cue(cue)`, `Take(layer?)`, `Clear(layer)`, `ClearAll`, `ClearGroup`.
  - Content: `Message`, `Prop(on/off)`, `LowerThird(name)`, `Timer(...)`, `MediaPlay`/`Pause`/`Stop`.
  - Integrations: `ObsScene(name)`, `AtemKey(key, on)`, `AtemMacro(index)`,
    `CompanionPress(slot, index)`.
  - Flow: `NextItem`, `PreviousItem`, `Wait(seconds)`, `RunMacro(name)`.
- Name clash: `:live-show` already has `Cue`, so the "cue" action needs another name (e.g.
  `ToPreview`).
- An `ActionRunner` in the app runs a list in order. `Wait` suspends, and a run can be cancelled.
  It dispatches through one host interface built from what exists:
  - `CalendarHost`/`fireCue` (`calendar/.../CueRunner.kt`) and `ScheduleActions`.
  - `OBSWebSocketManager.setScene`.
  - `AtemClient.setKeyOnAir`.
  - `CompanionSatelliteViewModel.pressButton`.
  - `LowerThirdSequencer.run`.
  - `goLiveAnnouncementTimer`.
  - `MediaViewModel.play`/`pause`.
- `CueItem`'s string `CueAction` (PROJECT, COUNTDOWN, GO_LIVE, SCENE, BLANK) maps onto `Action`, so
  existing cue sheets keep working.
- The declared-but-dead `OBS_SCENE`/`ATEM_KEY` become real. Update `FireCueTest`, which pins them
  as no-ops.
- New pieces:
  - "Next schedule item", built on `nextContentRow` (`calendar/.../model/CueEngine.kt`).
  - ATEM macro run: a new protocol command in `:atem`.
  - OBS scene listing, for pickers.

### Step 6 — Cue actions on schedule rows (UI → approval)

- Storage: `actions: Map<rowId, List<Action>>` in `ScheduleFileV2`
  (`schedule/.../ScheduleViewModelFiles.kt`), next to `notes` and `timing`. Old files read
  unchanged, and the map is in the undo snapshots.
- Firing: when a row's item reaches the air.
  - Hooked where go-live happens: `ScheduleRowHandlers.kt` `present*FromSchedule` and
    `remote/RemoteProjection.kt`.
  - With preview mode on, actions wait for Take via `PreviewBus.onAir` (already defers by cue
    identity).
  - Yield rules follow `CueRunner`'s, so an operator override wins.
- UI: an action list editor on the row (add, reorder, delete; pickers fed by OBS scenes, ATEM keys
  and macros, Companion buttons, lower thirds, props, messages).

### Step 7 — Macros (UI → approval)

- Storage: named action lists in settings (`MacroSettings`, additive).
- Triggers:
  - A macro button panel (sidebar or Companion surface).
  - Keyboard: binding a macro to a key extends `ShortcutAction` handling with dynamic macro
    entries in `KeyboardShortcutSettings`.
  - HTTP: `POST /api/macro/{name}`.
  - WS: `macro` command.
  - Companion: actions and variables in `COMPANION_API.md`.
- A macro can run another macro. Depth is bounded so loops can't hang.

### Step 8 — MIDI and OSC in and out (UI → approval)

- New module `:control-in`.
  - MIDI through `javax.sound.midi` (in the JDK): notes, CC and MSC.
  - OSC through a small UDP codec of our own (no MIDI/OSC dependency exists today; a dependency is
    added only if the design note decides so).
- A mapping table in settings: trigger → `Action` or macro, with a "learn" mode.
- Out: emit MIDI/OSC on go-live, clear and take, for lighting desks.
- Ports go through `testPort` in tests. The device layer is split from the mapping logic so the
  logic is unit-testable headless (AGENT.md's "split, don't exclude").

## Step 9 — Watchdog and output isolation (2.8)

1. **Watchdog first.**
   - A debug-build event-thread stall detector logs any frame over budget with the event thread's
     stack through `Log.warn`, which leaves a Sentry breadcrumb.
   - The thread-dump code from `jvmTest/.../HungTestReporter.kt` moves into `:diagnostics` and is
     shared.
2. **Fix the synchronous file checks on output paths it will flag:**
   - `presenter/.../FullScreenBackground.kt:96`
   - `presenter/.../SongPresenter.kt:72`
   - `presenter/.../LoopingVideoBackground.kt:42`
   - `lower-third/.../LottieRenderCache.kt:111`
   - `media/.../FollowerMediaUrl.kt:16`

   Each moves to a background check whose result is held in state.
3. **Isolation spike, then a decision recorded in the design note.**
   - Outputs share the event thread and the Compose snapshot system with the operator UI.
     `ComposeScenePump` confines its off-screen scenes to the event thread on purpose, because of
     a snapshot-observer lock inversion.
   - So a separate render thread inside one JVM is not viable without unshared state. The
     realistic option is an **output process** fed the program as plain data.
   - That builds on step 1: `program` as serializable cues plus the per-cue state.
   - Done when: a forced UI hang (a debug menu item that blocks the event thread) leaves every
     output running.
   - This is the riskiest step. Ship the watchdog and I/O fixes even if isolation slips.

## Parallel lanes (optional this phase)

- **2.6 ProPresenter import**, in `converter/.../song/ProPresenterConverter.kt`, in the order of
  `docs/IMPORT_SURVEY.md`'s list:
  - Arrangements → section order.
  - Libraries as songbooks.
  - `.proPlaylist` schedules.
  - Backgrounds.
  - Tempo and notes.
  - An import report.
- **3.4 EasyWorship import:** the same, after 2.6.
- **2.7 Release:**
  - A checksum or signature check on the downloaded installer (`utils/UpdateChecker.kt`,
    `dialogs/UpdateAvailableDialog.kt`).
  - Beta and stable channels.
  - Crash-free sessions confirmed in Sentry.
  - Delta updates last.

## Rules that apply throughout

- Commits are authored by zitlem
  (`-c user.name=zitlem -c user.email=88402063+zitlem@users.noreply.github.com`), with no co-author
  or bot byline.
- No edits to non-English locales.
- Never add to a detekt baseline. Never exclude from coverage without asking.
- New modules carry their own `AGENT.md`/`CLAUDE.md`, a row in the root AGENT.md module table, and
  the `test.yml`/`test-changed.sh` entries.
- Settings: additive fields need no version bump. A rename or restructure bumps
  `CURRENT_SETTINGS_VERSION` (currently 23) with a migration step and a test.

## Verification (per step)

- `bash test-changed.sh` while iterating. Then the touched modules' `:<m>:test`,
  `:<m>:jacocoTestCoverageVerification` and `:<m>:detekt`.
- `./gradlew :composeApp:jvmTest -PtestForks=2` and `jvmTestSerial`.
- New tests pass 3× with `--rerun-tasks`. No `Thread.sleep`. Each under ~1s.
- `./gradlew :composeApp:detekt` last.
- Steps 1–3 and 9: `./gradlew :composeApp:renderBenchmark` compared with
  `composeApp/benchmarks/results.md`. You run the soak on GitHub Actions.
- UI steps: show it and wait for approval. Then tests, then you re-record screenshots on the Mac
  (`recordRoborazziJvm`/`verifyRoborazziJvm`).
- End to end:
  - Run the app (`./gradlew :composeApp:run`).
  - Put a song live, then send `curl -X POST localhost:<port>/api/message` with a nursery
    template. Check the message goes up alone and clears after its duration.
  - Toggle a logo prop over a song.
  - Fire a schedule row whose actions switch an OBS scene and start a timer.
  - Trigger a macro from a key, from `/api/macro/{name}`, and from a MIDI note via a virtual MIDI
    port.
  - For step 9, use the debug "hang UI" item and check the outputs keep running.
