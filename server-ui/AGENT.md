# `:server-ui` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The Compose face of `:server`, which deliberately has no UI toolkit. It holds:
- the Server page of the Options dialog (`ServerSettingsTab`): the server card, the port and URL,
  the API key, the connection QR (`ConnectionQrDialog`), and its URL builders (`ServerUrls.kt`);
- the Remote Clients card (`ServerClientsCard`) and `RemoteClientManager`, the allow and block lists
  in `~/.churchpresenter/remote_clients.json`;
- the Companion triggers card (`CompanionTriggersCard`, `CompanionTriggerUrls.kt`): the URLs a
  Companion button calls for each lower third, the ATEM keys and the takedowns;
- calendar sync's card (`CalendarSyncCard`) and its phone invite (`CalendarEnrollQrDialog`);
- Instance Link's windows: `InstanceLinkDialog`, its layer picker (`InstanceLinkLayers`), its
  failure toast (`InstanceLinkToastHost`) and the status row the schedule sidebar shows
  (`ConnectionStatusRow`).

A real Gradle module of this build: `include(":server-ui")`, `implementation(projects.serverUi)`.
`:composeApp` is its only consumer: `OptionsDialog` draws the Server page, `MainWindowDialogs` the
Instance Link windows, `ScheduleSidebar` the status row, `CalendarManagerWindow` the invite, and
`MainWindowScope` holds the `RemoteClientManager`.

It takes `:server`, `:shared-ui`, `:strings`, `:settings`, `:theme`, `:calendar`, `:lower-third`
(which JSON files are lower thirds) and `:profiles` (the settings card kit), and nothing of
`:composeApp`'s.

## Seams to the app

- **`builtInRelayUrl`** on `ServerSettingsTab` and the calendar card: the relay calendar sync
  reaches when settings name none. It is `BuildConfig`'s, so `OptionsDialog` passes it.

## Package

**`org.churchpresenter.serverui`**, with the screenshot suites in `.screenshot`.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here.** The suites are `screenshot/ServerSettingsTabScreenshotTest`
  and `screenshot/CalendarSyncCardScreenshotTest`; their images are under `server-ui/screenshots/`.
- The suite runs on JUnit 5, so a class-level hook is `@BeforeAll`/`@AfterAll`; JUnit 4's
  `@BeforeClass` is silently never run here.
- `RemoteClientManager` persists `@Serializable` lists, so this module applies the serialization
  plugin; without it every save fails silently.
- The QR dialogs are real windows and cannot open headless; their content composables are what the
  suite draws.

## Commands

```bash
./gradlew :server-ui:test :server-ui:detekt
./gradlew :server-ui:jacocoTestCoverageVerification
./gradlew :server-ui:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :server-ui:verifyRoborazziJvm --tests '*ScreenshotTest*'
```

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**.
- **Coverage**: the root build's default six counters at 85% — no `coverageFloors`, no
  `coverageExcludes`.
