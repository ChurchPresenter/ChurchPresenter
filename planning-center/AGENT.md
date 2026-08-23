# `:planning-center` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Planning Center Online client**: the OAuth2 conversation, the Services/People REST calls, and
the one-shot loopback listener that catches the consent redirect. A real Gradle module of this
build: `include(":planning-center")`, `implementation(projects.planningCenter)`.

No credentials ship with it. Each church registers its own free PCO Developer application and
supplies its own client id and secret, the same bring-your-own-key shape as the Pexels/Pixabay
stock media client. **One-way pull only** — nothing is ever written back to Planning Center.

**The package is `org.churchpresenter.planningcenter`** — one package, flat. It held two of the
app's own (`…churchpresenter.data` for the client and formatter, `…churchpresenter.server` for the
callback listener). `:composeApp` still owns the first; the second has since become
`:companion-server`. Splitting this module's three files across two shared package names bought
nothing: they are one integration, and the `data`/`server` divide was an artifact of where they
happened to sit in the app.

## What lives here

| Path | Owns |
|---|---|
| `PlanningCenterClient.kt` | `object PlanningCenterClient` — OAuth token exchange and refresh, `/people/v2/me`, service types, plans, plan items, arrangement lyrics, attachment metadata, attachment download |
| `PlanningCenterLyricsFormatter.kt` | Chord-chart → plain lyrics, and PCO's `html_details` rich text → plain text |
| `PlanningCenterAuthServer.kt` | The loopback listener on the registered redirect port, started per connect attempt and torn down when the callback lands |

## What deliberately stayed in `:composeApp`

- **`data/PlanningCenterScriptureDetector.kt`** — it resolves references against a loaded `Bible`
  and falls back to `BibleBookAbbreviations`, which reads Compose string resources. It is glue
  between PCO text and the app's own Bible data, not part of the PCO protocol. It moves here only
  if the Bible data layer becomes a module and the abbreviations stop needing `Res`.
- **`viewmodel/PlanningCenterImportViewModel.kt`** and **`dialogs/PlanningCenterImportDialog.kt`** —
  Compose state and UI.
- **`data/settings/PlanningCenterSettings.kt`** — already in `:settings`, where every persisted
  settings class lives.

## Rules

- **Anything `:composeApp` calls has to be public here.** `internal` no longer reaches the app —
  which is why the `@Serializable` DTOs' construction test moved with the code, as
  `PlanningCenterDtoConstructionTest`.
- **Every request function returns an outcome; none of them throws.** Each catches exactly three
  things, and the order matters:
  1. `IOException` — refused, reset, timed out. A retry is worth offering: `NetworkError`.
  2. `UnresolvedAddressException` — no DNS answer, which is what being offline looks like. It is an
     `IllegalArgumentException` subclass, so it **must** be caught before the next clause or an
     offline operator is told their plan is malformed.
  3. `IllegalArgumentException` — the body was not what the API documents. `SerializationException`
     (not JSON at all) and the `jsonObject`/`jsonPrimitive` accessors (JSON of the wrong shape) both
     land here, and both mean `Failure`: retrying returns the same page.

  Anything else propagates, `CancellationException` included — a cancelled import must cancel, not
  come back as a network error. **Do not widen these back to `catch (e: Exception)`.**
- **The redirect port is `Constants.PLANNING_CENTER_OAUTH_PORT` (47850), from `:settings`.** It is
  the one thing this module reads from there, and it cannot be chosen at runtime: PCO OAuth apps
  require an exact pre-registered redirect URI, so `redirectUri()` and the callback server have to
  spell the same number the operator registered. Nothing else here may read a setting.
- **An attachment's `url` attribute is not a download link.** It is a `services.planningcenteronline.com`
  web-app link with browser-session auth; an OAuth bearer token 302s to the login page, which is
  what silently got saved as "the image" before this was found. Resolve the real file through
  `resolveAttachmentDownloadUrl` (a POST, as PCO's API requires) and then `downloadFile`.

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**, main and test both in scope.
  One `@Suppress` at the declaration, with its reason: `TooManyFunctions` on `PlanningCenterClient`,
  which is 14 request functions, one per PCO endpoint this integration uses. Its eight
  `TooGenericExceptionCaught` entries were **not** carried over from `:composeApp`'s baseline — the
  catches were narrowed to the three clauses above instead, which is what the rule was asking for.
- **Coverage**: the root build's default six counters at 85%, all of them — **no**
  `coverageFloors`, **no** `coverageExcludes`. It currently runs 92.6% instructions / 87.5% methods,
  the tightest counter being methods, so a new uncovered function is what will break it first.

## Tests

- **Nothing here touches the real Planning Center API.** Every request function takes
  `http: HttpClient = defaultHttp`, and the suites pass their own: `MockEngine` for the API calls,
  a local Netty host for the download and thumbnail paths.
- **The fake attachment host is one per class, started on first use.** It used to be one per test —
  eighteen Netty start/stops in a few seconds, each stopped with a zero grace period while the next
  bound — and roughly one run in six a host never reached the point of serving inside its deadline,
  failing whichever test happened to be next. Nothing mutates the host and each test downloads into
  its own temp dir, so there is nothing to isolate.
- **A "dead" port is port 1, never a closed `ServerSocket(0)`.** A port from an ephemeral socket
  closed again immediately goes straight back into the pool, and the next host binds port 0 and can
  be handed the same number: the download is then answered **404 by a server whose routes are gone**,
  which is `Failure`, not the `NetworkError` a refused connection gives. Port 1 is privileged, never
  bound, and refuses instantly.
- **`PlanningCenterAuthServerTest` binds the real 47850**, because that port is the point — it is
  the redirect the provider was told about, so `testPort()` would aim the callback somewhere PCO
  will never send it. That is safe here: this module's `test` task is a single JVM. It was in
  `:composeApp`'s `serialTestClasses` list for exactly this reason and was removed from it when the
  suite moved.

## Commands

```bash
./gradlew :planning-center:test
./gradlew :planning-center:detekt                            # gate — no baseline, must be clean
./gradlew :planning-center:jacocoTestCoverageVerification
```

All three run in CI, gated on this directory or the shared build files changing.

## Dependencies

`api(libs.ktor.client.core)` — `api` rather than `implementation` because every request function
takes an `HttpClient`, so the type is part of this module's public surface and has to resolve at a
caller that supplies its own engine. Then `ktor-client-cio` for the default engine,
`ktor-server-core`/`ktor-server-netty` for the callback listener only, `kotlinx-serialization-json`,
`:settings` for the one port constant, and `:diagnostics` for `CrashReporter.reportWarning`. No
Compose, and no dependency on `:composeApp` — the import UI depends on this module, never the
other way round.
