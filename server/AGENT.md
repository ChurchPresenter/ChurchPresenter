# `:server` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **companion server** and **Instance Link**: the Ktor REST and WebSocket API that phones,
tablets, Bitfocus Companion and other ChurchPresenter instances talk to. Also here:

- its routes and DTOs;
- the browser-source hub;
- the ATEM bridge;
- the Cloudflare tunnel and the self-signed SSL certificate;
- calendar sync through the relay;
- the Instance Link client, its view model and its log (`InstanceLinkLogger`);
- the remote-event types the approval dialog shows.

A real Gradle module of this build: `include(":server")`, `implementation(projects.server)`. A plain
Kotlin module with no Compose. Its consumers are `:composeApp` and `:live-output`.

It takes `:core-models`, `:settings`, `:shared-ui` (its usage events and text helpers),
`:diagnostics`, `:calendar`, `:lower-third`, `:presentation-engine`, `:atem`, `:dictionary`,
`:bible`, `:songs`, `:slides` and `:qa`, and nothing of `:composeApp`'s.

## Seams to the app

- **What a remote client asks for is applied by the app**, in `composeApp`'s `remote/` package:
  - `RemoteApply` applies a primary's live state to `PresenterManager`;
  - `RemoteProjection` projects a schedule item;
  - `RemoteScheduleItems` adds one to the schedule;
  - `RemoteMirroring` decides what a follower mirrors.

  They tie the live output, the statistics (`:statistics`) and the schedule together, so they stay
  app-side. Nothing in this module calls them; the app wires the server's callbacks to them.
- **Build values are passed in**:
  - `CompanionServer(appVersion = …)` is the app's `BuildConfig.APP_VERSION`.
  - The calendar relay is `RelayEndpoints`, given to `CalendarSyncService`. The app's
    `builtInRelayEndpoints` comes from GitHub Secrets. `RelayEndpoints.NONE`, the default, means no
    relay — what a build without the secrets has.
- **App tests that check wiring read public views**, not internals: `liveState`,
  `presentationIsLive`, `offersTranspose(index)`.

## Package

**`org.churchpresenter.server`**.

## Tests

- **JUnit 4 on junit-vintage**, like the app's suite: the tests use `@get:Rule TemporaryFolder` and
  `@BeforeClass`. The `kotlin-test` capability pin in `build.gradle.kts` keeps `kotlin-test` on its
  JUnit 4 flavour. Remove it, and those rules silently stop running.
- **One fork.** The suites bind real loopback ports, so `testPort(base)` (`ServerTestSupport.kt`)
  is the base itself. The ATEM upload suites the app had to run serially are serial here anyway.
- `TestSingletons.latchToTestHome()` / `latchSkikoNativeLibrary()` pin the once-per-JVM paths before
  a test swaps `user.home`, as in the app.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Don't add a dependency on `:composeApp`'s types.** Code that needs the live output, statistics
  or the schedule belongs in the app's `remote/`, reached through a callback.

## Commands

```bash
./gradlew :server:test :server:detekt
./gradlew :server:jacocoTestCoverageVerification
```
