# `:stt-engine` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The caption server's client, without the STT tab**: `STTManager`, the socket.io client for the
caption server, which also pulls the highlighted words and the Help Dev `.db` capture over REST.
What the server sends is parsed by `STTTranscript`, and the Help Dev capture is `STTCapture`; both
are `internal` parts of the manager.

An engine-layer module (root AGENT.md, "Layers"): `:stt` (the tab, with `api`), `:bible-tab` (its
auto-follow reads the transcript) and `:live-output` (the presenters and the stage monitor) use it.
It takes `:shared-ui` and `:core-models`, plus socket.io (`org.json` comes with it).

**The package is `:stt`'s own** (`org.churchpresenter.stt`): the files moved here unchanged, so no
import moved with them.

## Seams

- **`STTManager`** owns the socket and the four connection flags. Every listener `connect()`
  registers calls `onSocketEvent`, so each event can be driven without a live server.
- **`applyConnecting`, `applyConnected`, `applyDisconnected` and `applyConnectError`** are public.
  The app's own suites put the connection into a state with them.
- **The feeders** (test fixtures): `transcribe`, `translate`, `highlight` and `live` hand the
  manager what the server would send, and `SILENT_STT_URL` is a server that accepts and never
  answers, for a test that clicks Connect. The STT tab's and the Bible tab's suites use them.

## Commands

```bash
./gradlew :stt-engine:test :stt-engine:detekt
./gradlew :stt-engine:jacocoTestCoverageVerification
```
