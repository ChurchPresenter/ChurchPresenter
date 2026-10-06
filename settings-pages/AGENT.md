# `:settings-pages` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The pages the Options dialog hosts that need app services:
- **System** (`SystemSettingsTab`, the storage card and its prompts, `SpsConverter`, `AutoStartManager`)
  and the **Bible catalog** it opens (`BibleCatalogBrowserDialog`, `BibleCatalogViewModel`);
- **Projection** (`ProjectionSettingsTab` and its screen, NDI, OMT, Browser Source and ffmpeg cards);
- **Server** (`ServerSettingsTab`, `RemoteClientManager`, the clients, triggers and calendar-sync cards);
- **Companion Satellite** (`CompanionSatelliteSettingsTab`).

A real Gradle module of this build: `include(":settings-pages")`,
`implementation(projects.settingsPages)`. `:composeApp` is its only consumer. It ships its own
`composeResources`: the sample songs System installs (`files/song_samples`).

## Seams to the app

The app keeps `OptionsDialog` (the window that hosts every page), `CalendarEnrollQrDialog` and
`AppWindowRoot`, and hands the pages what only it has:
- `isReleaseBuild` on `SystemSettingsTab` and `ProjectionSettingsTab` — `BuildConfig.IS_RELEASE`.
  The default is `true`; tests of dev-only affordances pass `false`.
- On `ServerSettingsTab`: `windowRoot` (the app's `AppWindowRoot`, for the connection-QR window),
  `calendarInviteDialog` (the app's `CalendarEnrollQrDialog`) and `defaultRelayUrl`.

`SearchField` and `PaneTabRow` are generic and live here only because `:shared-ui` edits rerun nearly
every suite in CI; the app imports them from here.

## Package

**`org.churchpresenter.settingspages`**, one flat package; the screenshot suites are in
`org.churchpresenter.settingspages.screenshot`.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **JUnit 4 on junit-vintage**, as `:server`: the suites use `@get:Rule TemporaryFolder` and
  `@BeforeClass`, which a JUnit 5 run skips silently. Keep the `capabilitiesResolution` block.
- **Tests and screenshots live here.** The screenshot suites are under `screenshot/`; their images
  are under `settings-pages/screenshots/`. The Options dialog's own suite
  (`AppPreviewSettingsScreenshotTest`) and the enroll-QR shot stay in the app.

## Commands

```bash
./gradlew :settings-pages:test :settings-pages:detekt
./gradlew :settings-pages:jacocoTestCoverageVerification
./gradlew :settings-pages:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :settings-pages:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
