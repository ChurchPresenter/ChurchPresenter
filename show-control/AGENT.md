# `:show-control` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **action vocabulary** of `docs/SHOW_CONTROL.md`: one list of things that change the show, which
cue actions, macros, keys, the remote API and MIDI/OSC all speak.
- `Action`: every action, one type each, stored by the explicit `@SerialName` it carries.
- `ActionSerializer`: writes an action as a JSON object with a `type`, and reads one back; a `type`
  it does not know comes back as `Action.Unknown` and is written out unchanged.
- `ShowHost`: one call per action — what the app implements (`remote/AppShowHost.kt`).
- `ActionRunner`: runs a list in order on its own coroutine; `wait` suspends it, a macro runs in
  place (at most `MAX_MACRO_DEPTH` deep), a failing action is reported and passed over, and a run
  under a key replaces the one still going under it.

A real Gradle module of this build: `include(":show-control")`, `implementation(projects.showControl)`.
`:composeApp` and `:calendar` use it. It takes `:core-models` (an action can carry a schedule
item), kotlinx-serialization and coroutines, and nothing of `:composeApp`'s.

## Package

**`org.churchpresenter.showcontrol`**.

## Rules

- **Every action has an explicit `@SerialName`, and it never changes.** Files and settings keep
  actions by that name; `ActionSerializerTest` pins the whole set. A new action adds its name there
  and its serializer to `ActionSerializer.known`.
- **A layer is a string**, named as `:live-show` spells it (`SLIDE`, `MESSAGES`); this module does
  not depend on `:live-show`.
- **Flow is the runner's.** `wait` and `macro` never reach a `ShowHost`; everything else does, one
  call per action, through the exhaustive `dispatch`.
- **No Compose.** The host runs on whatever scope the app hands the runner.

## Commands

```bash
./gradlew :show-control:test :show-control:detekt
./gradlew :show-control:jacocoTestCoverageVerification
```
