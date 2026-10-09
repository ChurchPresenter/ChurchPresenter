# `:app-settings` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The settings that belong to the app as a whole rather than to one feature:
- the System page of the Options dialog (`SystemSettingsTab`): language, theme, tab labels
  (`TabLabelsRow`), start-up, crash reporting and the storage card (`SystemStorageCard`,
  `SystemStorageDetails`, `SystemStoragePrompts`), with its file and dialog actions behind
  `SystemSettingsActions`;
- the default calendar folder (`DefaultCalendarFolder`, `LocalDefaultCalendarFolder`);
- `AutoStartManager`, the launch-at-login entry on each OS, and `SpsConverter`, the old `.sps`
  songbook converter;
- the first-run setup wizard (`SetupWizardDialog` and the `SetupWizard*` step files), with the
  closing summary (`SetupSummary`);
- the sample songs the System page copies into a new library, as plain JVM resources under
  `src/main/resources/song_samples`.

A real Gradle module of this build: `include(":app-settings")`, `implementation(projects.appSettings)`.
`:composeApp` is its only consumer: `OptionsDialog` draws the System page, `MainWindow` opens the
wizard, and `main.kt` refreshes the auto-start entry.

It sits above the feature modules it configures (`:live-output`, `:media`, `:songs`, `:bible-tab`,
`:server`, `:updater`, `:profiles`) and takes nothing of `:composeApp`'s.

## Seams to the app

- **`frame`** on `SetupWizardDialog`: the window the wizard's content is drawn in. The app passes
  nothing and gets the real undecorated window; a test passes a plain box.
- **`LocalDefaultCalendarFolder`**: pins the calendar folder's fallback path so a picture does not
  print this machine's app-data folder.

## Package

**`org.churchpresenter.appsettings`**, with the screenshot suite in `.screenshot`.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here.** The suite is `screenshot/SystemSettingsTabScreenshotTest`;
  its images are under `app-settings/screenshots/`.
- The suite runs on JUnit 5: a class-level hook is `@BeforeAll`/`@AfterAll` and a temp folder is
  `@TempDir` (or `TempFolder.kt`) — JUnit 4's `@BeforeClass` and `TemporaryFolder` are silently
  never run here.
- No compose resource class is generated (`generateResClass = never`): strings come from
  `:strings`, icons from `:icons`, and the sample songs are read with `getResourceAsStream`.
- The storage prompts are blocking Swing dialogs and open through `afterDispatch`, never inline
  from a coroutine — see its KDoc.

## Commands

```bash
./gradlew :app-settings:test :app-settings:detekt
./gradlew :app-settings:jacocoTestCoverageVerification
./gradlew :app-settings:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :app-settings:verifyRoborazziJvm --tests '*ScreenshotTest*'
```

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**.
- **Coverage**: the root build's default six counters at 85% — no `coverageFloors`, no
  `coverageExcludes`.
