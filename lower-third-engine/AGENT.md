# `:lower-third-engine` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**What a lower third is played and uploaded with, without the tab**:
- the render cache (`render/`): `LottieRenderCache` prepares and reads cache entries. Its JSON and
  size policy is `LottieRenderSizes`, reached through the `LottieRenderPolicy` interface by
  delegation; its files, keys and eviction are `LottieCacheFiles`, and its frame codec is `ArgbRle`;
- `LowerThirdOffscreenRenderer`, which renders a lottie's frames off screen for the cache;
- `LowerThirdPresenter` (`presenter/`), the animation on an output. It takes the frame to draw as a
  plain `ImageBitmap`; `:live-output`'s `LottieFrameStream` owns the native bitmap behind it;
- the ATEM key sequencer (`LowerThirdSequencer`);
- `LottieFonts` and the bundled fonts it reads, under `src/main/resources/fonts`. LottieGen's
  `FontRegistry` reads the same resources off the classpath, so their path does not change.

An engine-layer module (root AGENT.md, "Layers"): `:lower-third` (the tab, with `api`),
`:presenter` (the Bible lottie band's fonts), `:live-output`, `:server` and `:server-ui` use it.
It takes `:shared-ui`, `:settings`, `:diagnostics`, `:atem`, `:lottieGenerator` and
`:presentation-engine`.

**The packages are `:lower-third`'s own** (`org.churchpresenter.lowerthird`, `.render`,
`.presenter`): the files moved here unchanged, so no import moved with them.

## Seams

- **`LowerThirdSequencer.run`** takes a `LowerThirdClip` (what runs), a `LowerThirdKey` (which ATEM
  key takes it on air) and an `AtemKeyDriver` — by default the switcher's keepalive connection. A
  test passes its own to make a key fail without the client's 5s timeout.
- **`LottieRenderCache.ensureForFile`** returns its background job, and **`awaitInFlight`** joins
  every render started. Nothing in the app waits on either; a test does.

## Rules

- The suite runs on JUnit 5, so a class-level hook is `@BeforeAll`/`@AfterAll`.
- A test that swaps `user.home` loads a skia class first, so skiko unpacks into the suite's own home
  (see `LottieRenderCachePrepareTest`). `LottieCacheFiles.cacheDir` reads `user.home` on every call
  for the same reason: eviction deletes files.
- `LowerThirdOffscreenRenderer` confines its scene to the event queue (root AGENT.md, "Tests");
  keep any new off-screen scene that way.

## Commands

```bash
./gradlew :lower-third-engine:test :lower-third-engine:detekt
./gradlew :lower-third-engine:jacocoTestCoverageVerification
```
