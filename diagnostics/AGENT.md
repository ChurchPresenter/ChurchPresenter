# `:diagnostics` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**Crash reporting**: the crash log written to disk, and the Sentry forwarding behind it. A real
Gradle module of this build: `include(":diagnostics")`, `implementation(projects.diagnostics)`.

**The package is `org.churchpresenter.diagnostics`.** It held
`org.churchpresenter.app.churchpresenter.utils` — the app's own utils package, shared with
`:composeApp` and `:settings` — which cost nothing at extraction time (no import changed) but left
three modules writing into one package name, so `CrashReporter` resolved in `:composeApp`'s utils
files with no import at all and the dependency was invisible there. It now has a package of its
own, and those files say what they depend on.

Nothing here is imported by prefix: a rewrite that touches `…churchpresenter.utils.*` wholesale
will drag `:composeApp`'s and `:settings`' own classes with it. Key on the two types this module
owns — [CrashReporter] and [BuildIdentity].

## What lives here

| Path | Owns |
|---|---|
| `CrashReporter.kt` | `object CrashReporter` — the local crash log, the Sentry bridge, PII scrubbing, the crash-escalation counter, and `BuildIdentity` |

47 files across `:composeApp` call it, which is why it is its own module rather than part of
`:settings`: everything that can fail wants to report, and nothing should have to depend on the
settings module to do so.

## Rules

- **`BuildConfig` is not available here.** It is generated into `:composeApp`. What the reporter
  says this build is arrives as [BuildIdentity], passed to `initialize` from `main.kt`. Do not add
  a dependency on `:composeApp` to get at it — that is the cycle this module exists to avoid.
- **Nothing here may read a setting.** Whether analytics reporting is on is a `Boolean` parameter,
  read from `AppSettings` by the caller. This module must never depend on `:settings`.
- **The paths are `get()`, not `val`.** `appDir`/`crashDir`/`runningFile` and friends resolve from
  `user.home` on every access. As fields they were fixed at first touch, which in a test suite that
  redirects `user.home` meant whichever class touched the reporter first pinned it to a directory
  that was later deleted. The doc comment at the declaration says this too; leave it alone.
- **Anything `:composeApp` calls has to be public here.** `internal` no longer reaches the app.

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**, main and test both in scope.
  One `@Suppress` at the declaration, with its reason: `TooManyFunctions` on `CrashReporter`, which
  is the single telemetry facade the whole app calls.
- **Coverage**: the root build's default six counters at 85%, all of them — **no**
  `coverageFloors`, **no** `coverageExcludes`.

  Reaching that needed two seams, both of the shape the root `AGENT.md` describes under **Tests**:
  - `startUp` takes `setUncaughtHandler` and `addShutdownHook` as parameters, because a default
    uncaught-exception handler and a JVM shutdown hook outlive the test that installed them. They
    are two parameters rather than one collector so each stays where it always was — the handler
    before `cleanOldLogs`, the shutdown hook last. `initialize` is the one-line production caller.
  - `configureOptions` applies every Sentry decision to a `SentryOptions` it is handed, so a test
    checks release, environment and sampling without `Sentry.init` installing a live SDK for the
    whole JVM. `crashAttachingBeforeSend`, `maskDsn` and `dsnFrom` are split out for the same
    reason.

  **`trace` is the one thing left uncovered**, and it cannot be reached: it is `inline`, so its
  body is compiled into the caller — a test's own class, which is not in the report. Its behaviour
  is pinned by `CrashReporterSentryTest` (`a traced block returns its own value`, `a traced block
  that throws lets the failure through`) even though the coverage does not show it.

## Test isolation — do not remove either system property

`build.gradle.kts` sets both on the `test` task:

- `user.home` → `build/test-home`. `CrashReporterTest` **deletes** `~/.churchpresenter/crash-reports`,
  `.install_id` and `.crash_count` in its `@BeforeTest`. In `:composeApp` that was safe because the
  `jvmTest` task redirects `user.home`; here this is the whole of that protection, and without it
  the suite deletes the developer's own crash reports on the first run.
- `sentry.dsn` → `""`. The real DSN lives in `:composeApp`'s `sentry.properties` and is not on this
  module's classpath, but the SDK auto-initialises from any properties file it finds, and an empty
  DSN leaves it permanently disabled.

**`no SentryAppender is attached to logback during tests` stayed in `:composeApp`**
(`LogbackSentryAppenderTest`). The appender comes from `sentry-logback`, a `:composeApp` dependency,
and `logback-test.xml` is `:composeApp`'s test resource — here the assertion would be vacuously true.

## Commands

```bash
./gradlew :diagnostics:test
./gradlew :diagnostics:detekt                            # gate — no baseline, must be clean
./gradlew :diagnostics:jacocoTestCoverageVerification
```

All three run in CI, gated on this directory or the shared build files changing.

## Dependencies

`api(libs.sentry)` — `api` rather than `implementation` because `CrashReporter.breadcrumb` takes a
`SentryLevel`, so the type is part of this module's public surface. Nothing else, ever: no Compose,
no `:settings`, no `:core-models`.
