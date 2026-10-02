# `:stt` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**Speech-to-text captions**:
- the STT tab (`STTTab`);
- `STTManager`, the socket.io client for the caption server. It also pulls the highlighted words
  and the Help Dev `.db` capture over REST.

A real Gradle module of this build: `include(":stt")`, `implementation(projects.stt)`. `:composeApp`
is its only consumer. The presenters, the stage monitor and the Bible tab's auto-follow read the
same `STTManager`.

It takes `:shared-ui`, `:strings`, `:settings` and `:theme`, plus socket.io (`org.json` comes with
it). Nothing of `:composeApp`'s.

## Seams to the app

- **`presentingMode`**: the tab needs only to know whether the output is showing captions, so it
  takes the output's mode as a `State<Presenting>`. `AppSTTTab` passes
  `presenterManager.presentingMode`.
- **`settingsDialog`**: the caption settings dialog edits the output profiles the app owns, so the
  app draws it into this slot.

## Layout

- **`STTManager`** owns the socket and the four connection flags. Every listener `connect()`
  registers calls `onSocketEvent`, so each event can be driven without a live server. What the
  server sends is parsed by `STTTranscript`, and the Help Dev capture is `STTCapture`. Both are
  `internal` parts of the manager.
- **`applyConnecting`, `applyConnected`, `applyDisconnected` and `applyConnectError`** are public.
  The app's own suites put the connection into a state with them.
- **The tab** is `STTTab`, with its connection bar, status line and live preview in
  `STTTabParts.kt`.

## Package

**`org.churchpresenter.stt`**. The test helpers (`sttTab`, `STTLabel`, the `transcribe`/`translate`/
`live` feeders, the finders and `SILENT_STT_URL`) are in `src/testFixtures`. They are public, so the
app's screenshot suites can use them.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests live here**, beside the code. The tab's screenshots stay in the app
  (`STTTabScreenshotTest`), where they were recorded.

## Commands

```bash
./gradlew :stt:test :stt:detekt
./gradlew :stt:jacocoTestCoverageVerification
```
