# `:companion-server` — the HTTP/WebSocket surface the desktop exposes

Everything that speaks to something outside this process: the wire format, the routes, the pages the
server hands a browser, TLS, the tunnel, and the client a **follower** instance consumes the same
surface with. One `CompanionServer` serves all of it — the phone companion app, the Q&A page people
scan a QR code for, the Browser Source overlay OBS pulls, the presentation remote, and Instance Link.

**What a remote request then does to the app is not here.** That is `:composeApp`, under
`remote/` — `RemoteApply.kt` turns a `LiveStateDto` into calls on `PresenterManager`, and
`RemoteApproval.kt` turns an access verdict into the dialog or toast the operator sees. Those need
view models, `Presenting`, and Compose key events; this module has none of the three and must not
grow them.

## Not to be confused with

| module                 | what it is                                                              |
|------------------------|-------------------------------------------------------------------------|
| `:companion-server`    | **this** — the server the phone app, OBS and a follower instance talk to |
| `:companion-satellite` | a *client* of Bitfocus Companion, for Stream Deck surfaces — unrelated  |
| `:atem`                | the Blackmagic protocol client; `AtemBridge` here is the app-side wiring |

## What `:composeApp` uses from it

`CompanionServer` itself (started from `main.kt`, wired through `MainDesktop` and the settings
tabs), `InstanceLinkClient`, the DTOs the follower paths read, `LottieRenderCache`,
`LowerThirdSequencer`, `RemoteAccess`/`remoteAccessDecision`, and `InstanceLinkLogger`.

## The boundary, and the five things it lets through

The module compiles without Compose and without any app type. What routes genuinely need from the
app arrives as **values on one object**, `CompanionHost`:

| field                     | why it cannot live here                                             |
|---------------------------|---------------------------------------------------------------------|
| `appVersion`              | `BuildConfig` is generated into `:composeApp`                        |
| `onMobileClientConnected` | `UsageEvents` is the app's own counter                               |
| `decodeHeicToJpeg`        | `HeicDecoder` is an app image utility, used by the picture tab too   |
| `loadSongs`               | `Songs` holds Compose snapshot state                                 |
| `lottieRenderer`          | drawing a lottie needs Compose and a Skia surface — see below        |

Every field is defaulted to something inert, so **a test starts a real server with no app at all**
and a route that needs one answers "not available" instead of failing. `appCompanionHost()` in
`:composeApp` builds the real one.

Q&A is the sixth thing and has its own shape: `QaStore`, implemented by the app's `QAManager`. The
server used to hold `QAManager` directly, which both crossed this boundary and broke the repo rule
against passing a view model into another class.

**Adding a sixth `CompanionHost` field is a decision, not a detail.** Each one is something the
server knows about the app, and the whole point of the module is that the list stays short. Ask.

### `LottieFrameRenderer` — no global, no `var`

`LottieRenderCache` owns the `.lrcc` clip format: the header, the RLE, the footer offsets, the
reader. It does not own the drawing. `prepare`, `ensureForFile` and `ensureForFolder` each take a
`LottieFrameRenderer` **explicitly**, so a forgotten wiring is a compile error rather than a screen
full of blank lower thirds. `:composeApp` passes `SkiaLottieFrameRenderer`; this module's own suite
passes `FakeLottieFrameRenderer`, which is why skia is not on the test classpath here at all.

Do not "simplify" this into a mutable property on the object that the app installs at startup.

## Layout

```
CompanionServer.kt        the server: lifecycle, the state clients read, the broadcast fan-out
CompanionHost.kt          the boundary above — CompanionHost, LottieFrameRenderer, QaStore
CompanionApiDtos.kt       the wire format. Public: :composeApp builds and reads these too
*Routes.kt                one `Route.xxxRoutes(server, …)` group per area, registered by the server
CompanionWebPages.kt      the HTML/JS the server serves directly (Q&A, remote, browser source)
InstanceLinkClient.kt     the follower side: connect, reconnect, heartbeat, command acks
SslCertificateManager.kt  self-signed certificate generation for the HTTPS listener
TunnelManager.kt          the public-access tunnel process
LottieRenderCache.kt      the .lrcc pre-render cache; AtemBridge uploads out of it
PresentationStore.kt      decks parsed once and re-read only when the file changes
```

## Commands

```bash
./gradlew :companion-server:test     # the suite
./gradlew :companion-server:detekt   # the gate
./gradlew :companion-server:jacocoTestReport
```

## Why branch and complexity have floors

`extra["coverageFloors"]` sets **BRANCH to 0.80 and COMPLEXITY to 0.75**. The other four counters
are on the root build's 85% default and pass: instruction 91.3%, line 93.7%, method 87.5%, class
97.8% — against measured branch 81.5% and complexity 76.1% (801 tests, measured 2026-08-23). There
are **no `coverageExcludes` at all**, so every one of those figures is over every class the module
compiles rather than over a filtered subset.

The reason neither reaches 85% is structural, not a gap in the suite. Every `suspend` block
compiles to an `invokeSuspend` with a `switch` over its continuation label, and each suspension
point is a branch that only counts when the coroutine actually parks there. This module is almost
entirely suspend lambdas — a Ktor route *is* one — so a large share of its complexity belongs to
code nobody wrote and no test can reach. Measured across the remaining misses:

| where the uncovered complexity lives | branches | complexity |
|---|---|---|
| Kotlin coroutine state machines (`$1` suspend lambdas) | 219 | 206 |
| generated `$Companion` / `$serializer` | 51 | 59 |
| real named classes | 189 | 263 |

**Do not read the floor as permission to stop testing.** It was set only after the suite went from
669 to 795 tests specifically chasing these two counters, and the returns were measured at each
step: 83 tests bought 1.2 points of branch, the next 23 bought 0.4, the next 21 bought 0.2. What
those tests found on the way is the better argument for having written them — the Q&A routes had
seven route groups at 0.0%, `InstanceLinkClientFetchGuardTest` covered every refusal and no
successful fetch (a client returning `null` for everything would have passed all fourteen of its
tests), and three WebSocket command types had never been sent by any test in the repo.

Both floors sit just under the measured value rather than comfortably below it, so they ratchet: a
real regression trips the gate instead of being absorbed by slack. **Raise them as the numbers rise;
do not lower them.** If a change genuinely cannot hold the current figure, that is a conversation
rather than an edit.

## The detekt baseline

`config/detekt/baseline.xml` holds **33 IDs covering 39 findings**, all of which arrived with the
code from `:composeApp`, where every one was already suppressed in the root
`config/detekt/baseline.xml`. That was verified rather than assumed: this module's detekt was
pointed at the root baseline as a one-off measurement, and everything matched except a single ID
that differed only in the package name embedded in it. (33 IDs rather than 39 findings because
detekt stores them as a set — several identical `catch (e: Exception)` sites in one file share one
ID, so suppressing that ID suppresses all of them. Read the count as "39 findings", not "33".)

It breaks down as 15 `LongParameterList` (route groups taking seven to fifteen `MutableStateFlow`s
each), 13 `TooGenericExceptionCaught`, and 5 `TooManyFunctions`.

**There are no `LongMethod` or `MaxLineLength` entries and there are not to be any.** Both were
fixed outright at extraction time rather than baselined: 77 over-long lines wrapped, and all six
long methods split — the four Q&A/remote pages and the Browser Source overlay had their `<style>`
and `<script>` blocks lifted into private vals (verified byte-identical against the pre-extraction
commit), and `handleWsCommand` went from a 177-line `when` to four handlers tried in order.

Two things worth knowing before touching that split:

- **`LongParameterList` and `LongMethod` fire at `>=` the threshold, not `>`.** A seven-parameter
  constructor trips `constructorThreshold: 7`, and a 100-line function trips `LongMethod: 100`.
  The first attempt at the `handleWsCommand` split traded one finding for four by not knowing this.
- **`WsCatalogs` exists to keep `WsCommandContext` at five fields.** The three id→label lookups are
  grouped because they are used together — every operator prompt raised from a command frame names
  the picture folder, the deck or the schedule row the request is about. Do not flatten it back.

**Do not add to the baseline.** A finding in new code is to be fixed. If something genuinely cannot
be, raise it rather than appending an ID.

## Rules

- **Ports go through `testPort()`** (`src/testFixtures`), which shifts this module's band clear of
  `:composeApp`'s four fork bands. The two suites can be running at the same time.
- **`LogHomeLatch.latch()` first in `@BeforeTest`**, before any `user.home` swap.
  `InstanceLinkLogger` resolves its directory in a `by lazy` and keeps whatever it saw first.
- **The suites are JUnit 4** — `@BeforeClass`, `@get:Rule TemporaryFolder`, `Assume`. The build file
  names `kotlin-test-junit` explicitly and resolves the capability conflict to it; without that,
  variant selection picks junit5 unopposed and those annotations resolve to nothing **silently** —
  313 tests here ran with no `@BeforeClass` and failed on uninitialised `lateinit` fields.
- **A route that answers before it finishes leaves a coroutine behind.** `POST /api/atem/still|clip`
  responds `"uploading"` and transfers on the server's own scope. Every test in such a suite must go
  through `AtemBridge.trackUpload`/`cancelUpload`; one that drops the connection instead poisons the
  next suite's switcher. See the same rule, at length, in the root `AGENT.md`.
