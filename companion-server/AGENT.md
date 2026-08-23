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
./gradlew :companion-server:detekt   # the gate — no baseline, and it is not to acquire one
./gradlew :companion-server:jacocoTestReport
```

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
