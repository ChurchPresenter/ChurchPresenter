# `:obs` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **OBS Studio** integration: switching OBS scenes as the live content changes. It holds:
- `OBSWebSocketManager`, the obs-websocket v5 client (Hello, Identify with the authentication
  digest, Identified, then `SetCurrentProgramScene` requests);
- `obsSceneFor`, which scene a content type switches to, from `OBSSettings`;
- `OBSSettingsTab`, the OBS page of the Options dialog.

A real Gradle module of this build: `include(":obs")`, `implementation(projects.obs)`.
`:composeApp` is its only consumer: `ObsSceneWiring.kt` calls `obsSceneFor` when the presenting mode
changes, `AppRootState` owns the manager, and `OptionsDialog` draws the page.

It takes `:shared-ui`, `:strings`, `:settings`, `:theme` and `:diagnostics`, plus the Ktor client,
and nothing of `:composeApp`'s. `OBSSettings` itself stays in `:settings` with everything else the
app persists.

## Package

**`org.churchpresenter.obs`**.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here**, beside the code: `ObsSettingsTabScreenshotTest`, images under
  `obs/screenshots/`.
- **Tests never need a real OBS.** `OBSWebSocketManagerTest` runs a fake OBS on an OS-assigned port;
  `ScriptedObs` plays any other script, including handshakes a real OBS never sends.
  `BlackHoleSocket` holds a connection in CONNECTING without waiting on a timeout.

## Commands

```bash
./gradlew :obs:test :obs:detekt
./gradlew :obs:jacocoTestCoverageVerification
./gradlew :obs:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :obs:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
