# `:updater` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The in-app updater. It holds:
- `UpdateChecker`, the GitHub releases check: the newest release with an installer for this OS and
  CPU, pre-releases only when asked, and the count-only download beacon to churchpresenter.org;
- `UpdateAvailableDialog`, the update window (Help → Check for Updates, and the check at launch),
  and `UpdateAvailableContent`, everything it shows;
- the download (`UpdateDownload.kt`): `downloadInstaller` into the temp directory and
  `UpdateDownloadFlow`, the Download → Install Now sequence the window drives;
- `installAndQuit` (`UpdateInstall.kt`), which starts the installer natively and quits;
- `deleteLeftoverUpdateInstallers`, which clears the previous update's installer at launch;
- `StorePackage`, whether this is a Microsoft Store install (the Store updates those itself).

A real Gradle module of this build: `include(":updater")`, `implementation(projects.updater)`.
`:composeApp` is its only consumer: `main.kt` initialises it and clears old installers,
`MainWindow` runs the launch check, the menu's Check for Updates (`MainWindowChrome`) runs a manual
one, `MainWindowDialogs` opens the window, and `AutoStartManager` reads `StorePackage`.

It takes `:shared-ui`, `:strings`, `:settings` and `:theme`, and nothing of `:composeApp`'s.

## Seams to the app

- **The build identity.** `BuildConfig` is generated into `:composeApp`, so `main.kt` passes
  `UpdaterIdentity(appVersion, isRelease)` to `UpdateChecker.initialize` once at startup, as it does
  `BuildIdentity` for `CrashReporter`. Uninitialised, the updater is a dev build of an unknown
  version: every release is newer, and the beacon is never sent.

## Package

**`org.churchpresenter.updater`**, with the screenshot suite in `.screenshot`.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests, and the window's screenshots, live here.** The suite is
  `screenshot/UpdateDialogScreenshotTest`; its images are under `updater/screenshots/updateDialog/`.
- **Tests never reach the network or the machine.** The release check and the beacon run against a
  local `HttpServer`; the beacon returns its job so a test joins it rather than waiting. The window's
  steps (`UpdateSteps`) and `installAndQuit`'s launch and quit are handed in as fakes. What only the
  real machine can do — the `DialogWindow`, starting a native installer, `exitProcess`, the live
  GitHub call — is not reached by the suite.
- **Nothing unverified is run.** `UpdateChecker` carries the release asset's `digest` (GitHub's
  `sha256:…`) as `UpdateInfo.downloadSha256`; `downloadInstaller` hashes the file as it lands and
  keeps it only on a match. A missing digest or a mismatch deletes the file and reports
  `DownloadState.Error(unverified = true)`, and the window offers the release page instead.

## Commands

```bash
./gradlew :updater:test :updater:detekt
./gradlew :updater:jacocoTestCoverageVerification
./gradlew :updater:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :updater:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
