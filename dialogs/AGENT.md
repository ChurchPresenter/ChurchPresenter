# `:dialogs` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The app's dialogs and small windows: About, Contact Us, the keyboard shortcuts, Customize Theme, the
remote-permission and remote-control dialogs (`RemoteEventDialog`, `PresentationRemoteDialog`,
`QARemoteDialog`, `RemoteActivityToast`), the show-control dialogs (Control, Macros, Props, Message,
Clear groups), Add Label, STT settings, the memory monitor, the crash-feedback and
already-running dialogs, and the frames they open in (`DialogFrame`, `ToolWindowFrame`,
`DialogSizes`).

A real Gradle module of this build: `include(":dialogs")`, `implementation(projects.dialogs)`. Its
only consumer is `:composeApp`. It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`,
`:settings`, `:theme`, `:telemetry`, `:live-show`, `:live-output`, `:control-in`, `:schedule`,
`:helper`, `:server`, `:calendar`, `:profiles` and `:songs`, and nothing of `:composeApp`'s.

**Package:** `org.churchpresenter.dialogs`.

## What stays in the app

`OptionsDialog` (it hosts every module's settings page), `ToolWindows` (the converter, song library,
calendar and generator windows), `LicenseDialog` and `ShareYourStoryDialog` (they read
`:composeApp`'s own resources) remain in `:composeApp`'s `dialogs` package, with their tests.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **What only the app knows comes in as a parameter.** The build's identity is a
  `TelemetryIdentity` (`AboutDialog`, `ContactUsDialog`), the already-running dialog's theme is a
  `ThemeCustomization` — never an import of `BuildConfig` or anything else of `:composeApp`'s.
- **Tests and screenshots live here.** The screenshot suites are under `screenshot/`
  (`AlreadyRunningDialog`, `CustomizeThemeDialog`, `KeyboardShortcutsDialog`,
  `PresentationRemoteDialog`, `QARemoteDialog`); their images are under `dialogs/screenshots/`.
  `RemoteEventDialogScreenshotTest` stays in the app: it labels its events with the app's
  `remote/` helpers. `ViewportAssertions.kt` is this suite's copy of the app's intrinsic-height
  helpers; `TestIdentity.kt` is the identity the tests pass.

## Commands

```bash
./gradlew :dialogs:test :dialogs:detekt
./gradlew :dialogs:jacocoTestCoverageVerification
./gradlew :dialogs:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :dialogs:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
