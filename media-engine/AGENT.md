# `:media-engine` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The playback under the Media tab, without the tab**: `MediaViewModel` (split by concern into
position, looping, audio, subtitles and a cue's pending playback) and `LocalMediaViewModel`; the
players (`VideoPlayer`, `SoftwareVideoPlayer`, `EmbeddedPlayback`, `PlaybackSync`, the shared frame
buffer); VLC discovery (`VlcAvailability`, `VlcInstall`, `VlcAudioDevice`); `EmbeddedVideoDecoder`,
which a presentation's video layers play through; `MediaPresenter` and `SubtitleOverlay`; the
subtitle parser; `MediaDuration` and `VideoFirstFrame`.

An engine-layer module (root AGENT.md, "Layers"): `:media` (the tab, with `api`), `:presenter`,
`:canvas-engine`, `:live-output`, `:profiles`, `:app-settings` and `:telemetry` use it. It takes
`:shared-ui`, `:settings`, `:diagnostics` and vlcj.

**Packages are `:media`'s own** (`org.churchpresenter.media.viewmodel`, `.composables`,
`.presenter`, `.subtitles`, `.utils`): the files moved here unchanged, so no import moved with them.

## Rules

- **`MediaViewModel` is the root AGENT.md's rendering-bridge exception**: an output window's video
  panel is tied to the view model's lifecycle, so the outputs receive it (`OutputSurface`,
  `LocalMediaViewModel`). It is the only view model passed around; don't add another.
- **Opening VLC is one step a test can replace**: `openSoftwareVlc` (`SoftwareVideo` and
  `EmbeddedVideoDecoder` take it as a parameter). Keep any new device call the same shape.
- **`user.home` is the module's own** (`build/test-home`): `VideoFirstFrame` caches under it.

## Commands

```bash
./gradlew :media-engine:test :media-engine:detekt
./gradlew :media-engine:jacocoTestCoverageVerification
```
