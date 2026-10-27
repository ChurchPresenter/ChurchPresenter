# `:signage-core` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **signage screens' wire types**, and the **hub of paired screens** the companion server and the
Signage tab share. A foundation-layer module with no
Compose: `:server` and `:signage` both depend on it, and `:composeApp` makes the one `SignageHub`
and hands it to both. `SignageHub.pairedScreensByPlatform` reads the paired-screen counts from disk
without starting a hub, for the launch ping.

The player itself lives in its own repo, `ChurchPresenter/ChurchPresenter-Signage`.

## Layout

| Package | Holds |
|---|---|
| `org.churchpresenter.signage.core` | **A copy** of the signage repo's `:core`: `Manifest`, `ScheduleBlock`, `ScreenOverride`, `PlayerMessage`/`ServerMessage`, `decide()`, `SignageJson`, `PROTOCOL_VERSION` |
| `org.churchpresenter.signage.hub` | `SignageHub` and its parts: `ScreenRegistry` (paired screens and their token hashes, `screens.json`), `PairingQueue` (players waiting for approval), `ScreenLinks` (who is connected, what they can do, what they report — status, previews, update progress, the web page test — and messages on their way), `AgentUpdates` (the latest signed player release from GitHub, and each platform's build, downloaded and checked only when a screen is updated) |

## Rules

- **The `core` package is a copy, kept to the contract.** The signage repo's `docs/PROTOCOL.md`
  and `core/src/test/resources/fixtures/manifest-v1.json` are the contract; the copied tests
  (`WireFormatTest` against the same fixture) hold this side to it. Change it only by copying the
  signage repo's `core` again, never by editing here first. Say which signage commit it came from in
  the commit message.
- **Tokens are kept only as SHA-256 hashes**, compared with `MessageDigest.isEqual`.
- **No I/O on the caller's thread**: `ScreenRegistry.load()` is a suspend function on
  `Dispatchers.IO`, and every save is launched there, one at a time.

- **Player updates are trusted only through a signed release index**: `ReleaseSigning.PUBLIC_KEY`
  (copied with the core) must match the signage repo's; `AgentUpdates` keeps a build only when it
  hashes to the signed entry. `-Dchurchpresenter.signageReleaseUrl=` (a `file:` folder too) tries a
  release before it is published.

## Commands

```bash
./gradlew :signage-core:test :signage-core:detekt
```
