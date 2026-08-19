# ChurchPresenter LottieGen

A standalone Compose Desktop application for generating animated lower-third overlays as Lottie JSON files.

Built with Kotlin Multiplatform + Compose Desktop. Can run standalone or embedded inside [ChurchPresenter](https://github.com/ChurchPresenter/ChurchPresenter).

## Features

- **12 animation styles** — Bar, Boxed, Circular, Banner, Gradient Bar, Line Split, Random Fade, Diagonal, Diagonal Wipe, Double Line, News Ticker, News Badge
- **Live preview** — Real-time Lottie animation preview with play/pause and seek
- **Full customization** — Text, fonts (12 families), colors, shape, logo, timing, position, canvas size
- **Preset system** — Save and load animation presets
- **Color theme library** — 8 built-in themes + save custom themes (shared with ChurchPresenter)
- **Logo support** — Import and embed logos from file system
- **Cross-platform** — Windows (MSI), macOS (DMG), Linux (DEB)

## Running Standalone

```shell
./gradlew run
```

## Animation Style Editor (developer tool)

A developer-only "video editor for animation styles": author a new style as a declarative
spec document (elements, slots, keyframe tracks) with instant live preview, a scrubbable
timeline, and a 12-cell test matrix (3 alignments × logo × background) — no per-iteration
rebuild. Finished specs ship compiled into releases like every other style.

- Standalone: `./gradlew run --args="--editor"`
- Inside ChurchPresenter: **Developer → Animation Style Editor…** (dev builds or
  `CHURCHPRESENTER_FORCE_DEV_WINDOW=true` only)
- Authoring/registration workflow: see **Path B** in [ADDING_ANIMATIONS.md](ADDING_ANIMATIONS.md)

## Building Installers

```shell
# Windows
./gradlew packageMsi

# macOS
./gradlew packageDmg

# Linux
./gradlew packageDeb
```

Requires JDK 21.

## Integration with ChurchPresenter

This project lives inside the ChurchPresenter repository as its own module. Its source is compiled as part of the main app via `kotlin.srcDir` and launched as a separate Compose window from the Lower Third settings.

When running inside ChurchPresenter:
- Canvas size defaults to the presenter display resolution
- Files save directly to the configured lower third folder
- The file list refreshes automatically after saving

## Project Structure

```
src/main/kotlin/lottiegen/
├── Main.kt, App.kt              # Entry point and root composable
├── model/                        # Config, Preset, ColorTheme, Enums
├── lottie/                       # Core Lottie JSON generation engine
│   ├── LottieBuilder.kt          # JSON builder
│   ├── LottieGenerator.kt        # Main generate() entry point
│   ├── KeyframeUtils.kt          # Keyframe and easing utilities
│   ├── ShapeHelpers.kt           # Shape primitives (rect, ellipse, path, fill, stroke)
│   ├── TextHelpers.kt            # Text layer and animator construction
│   ├── ColorUtils.kt             # Hex to Lottie RGB conversion
│   ├── TextMeasurer.kt           # AWT-based text measurement
│   ├── FontRegistry.kt           # Bundled font loading
│   └── styles/                   # 12 animation style generators
├── viewmodel/                    # State management with debounced generation
├── ui/                           # Compose UI (control panel, preview, components)
└── persistence/                  # Preset, color theme, and logo file I/O
```

## Adding a New Animation Style

- 🎨 **[ANIMATION_DESIGN_BRIEF.md](ANIMATION_DESIGN_BRIEF.md)** — code-free creative brief for
  proposing a new animation *concept* (hand this to a design agent)
- 🔧 **[ADDING_ANIMATIONS.md](ADDING_ANIMATIONS.md)** — technical checklist for implementing an
  approved concept as a new style
