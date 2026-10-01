# 📚 ChurchPresenter Documentation

| File | What it holds |
|------|---------------|
| [CODING_STANDARDS.md](CODING_STANDARDS.md) | The style rules: imports, string resources, Material 3, type names |
| [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md) | Workflow, verification commands, audit status, contributing |
| [AGENT.md](AGENT.md) | Architecture, modules, commands, screenshot and test rules (read by coding agents, useful to anyone) |
| [FEATURES.md](FEATURES.md) | Every feature and where its source lives |
| `<module>/AGENT.md` | Each module's own layout, commands and rules |
| [BUILD_INSTALLERS.md](BUILD_INSTALLERS.md), [QUICK_START_INSTALLERS.md](QUICK_START_INSTALLERS.md) | Building the installers |
| [GRAPHICS_BACKEND.md](GRAPHICS_BACKEND.md) | Choosing the GPU backend on a machine, and what it does to screen capture |

## Before every commit

```bash
bash cleanup_check.sh          # wildcard imports, Material 2, prints, fully qualified names, unused code
./gradlew :composeApp:detekt   # CI's first gate — run it last
```

## File locations

- **String resources:** `strings/src/main/composeResources/values/strings.xml` (English only)
- **Constants:** `settings/src/main/kotlin/org/churchpresenter/settings/utils/Constants.kt` (anything
  persisted or shared with the settings) and
  `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/utils/Constants.kt` (the app's
  own)

Older notes (`CODING_STANDARDS_SUMMARY.md`, `TODO_CLEANUP.md`, …) are kept in `docs_archive/` for
history only.
