# `:icons` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The app's images, and no Kotlin source. A real Gradle module of this build — `include(":icons")`,
`implementation(projects.icons)`. `:composeApp` is its only consumer.

| Path | Holds |
|---|---|
| `src/main/composeResources/drawable/` | The UI drawables — the `ic_*` vector icons and the share-story screenshots |
| `src/main/resources/app-icon/` | `icon-<n>.png`, the frames `AppWindowIcons` hands every window (title bar, Alt+Tab), read off the classpath as `/app-icon/icon-<n>.png` |

**The installer icons are not here.** `icon.icns`, `icon.ico` and `appResources/*/icon*.png` stay in
`composeApp/src/jvmMain/appResources/`, because jpackage reads them from that directory alongside the
bundled ffmpeg and native libraries. `composeApp/tools/generate_windows_icon.py` writes both places.

## Package

The Compose Resources class is generated into `org.churchpresenter.icons.generated.resources`, set by
`packageOfResClass` in `build.gradle.kts`. Code that uses no strings imports it as `Res`
(`Res.drawable.ic_play`). Beside the strings' `Res` (`:strings`) it is imported as
`Res as IconRes` (`IconRes.drawable.ic_play`).

## Rules

- **Every icon is a real asset** — never text or emoji (root `AGENT.md`, UI icons).
- A vector drawable bakes `android:fillColor="#FF000000"`; draw it through `Icon` or a tint, never an
  untinted `Image` (see `TooltipIconButton`).

## Commands

```bash
./gradlew :icons:compileKotlin   # regenerate the Res accessors
```

No tests, no detekt and no JaCoCo: the module has no hand-written code.
