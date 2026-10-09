# ChurchPresenter Development Guide

> Workflow, verification and contributing. The style rules themselves are in
> [CODING_STANDARDS.md](CODING_STANDARDS.md); agent-specific rules, architecture and the test rules
> are in [AGENT.md](AGENT.md).
>
> Last updated: 2026-09-29

---

## 🎯 Zero Tolerance Policy

The following are **NOT** acceptable in any commit (each is explained in CODING_STANDARDS.md):

❌ Wildcard imports (`import ... .*`)
❌ Hardcoded UI strings (`Text("Save")`)
❌ Magic strings in logic (`if (type == "song")`)
❌ Unnamed color values (`Color(0xFF123456)`)
❌ Material 2 components (`androidx.compose.material.*`, other than `material.icons`)
❌ Text or emoji used as an icon (`Text("✕")`)
❌ Unused imports
❌ Print statements (`println()`, `print()`, `System.err.println()`) — diagnostics use `Log`
❌ Commented-out code blocks
❌ Fully qualified type names where an import would do (`androidx.compose.ui.unit.Dp`)

**All violations must be fixed before merging!**

---

## 📝 Strings and constants

**User-facing strings** go in `strings/src/main/composeResources/values/strings.xml` —
English only; the other locales are managed separately (see AGENT.md). Modules with their own UI
(`calendar`, `songlibrary`) keep their own `composeResources`; `converter` and `lottieGenerator` use
a `ResourceBundle`-backed `Strings`.

Naming convention:
- Actions: `action_save`, `action_delete`
- Labels: `label_song_title`, `label_author`
- Tooltips: `tooltip_move_up`, `tooltip_add_to_schedule`
- Messages: `message_no_songs_found`
- Errors: `error_file_not_found`

A string that stops being referenced is deleted in the same change, from `values/` and from every
`values-*/` (deleting a key is not translating it).

**Technical strings** are constants:
- `settings/src/main/kotlin/org/churchpresenter/settings/utils/Constants.kt` — anything persisted or
  shared with the settings (keys, target types, sort keys, background types).
- A closed set of ids that belongs to one feature gets its own small object next to it
  (`SongColumnId`, `PcoItemType`).

---

## 🔍 Verification

### Before every commit

```bash
bash cleanup_check.sh          # wildcard, Material 2, prints, FQN, unused-code counts; exits non-zero on failure
./gradlew :composeApp:detekt   # CI's first gate — run it last
```

`cleanup_check.sh` checks `composeApp/src/` in full and every module of the build for wildcard
imports, Material 2, prints and fully qualified names (listing only the modules with a finding),
then compiles everything for the unused-code counts. Run each touched module's `detekt` as well.

### Detailed checks

```bash
# Hardcoded UI strings. Grep the *literals*, then read them — a literal that is not the first
# argument to Text( (inside a conditional, a label =, a supporting =) is what the simple grep misses.
grep -rnE '(^|[^a-zA-Z.])Text\("[^"]' --include="*.kt" composeApp/src/jvmMain/kotlin/ \
  | grep -v stringResource | grep -vE 'respondText|Frame\.Text' | grep -vE 'Text\("[^a-zA-Z]*"'

# Sentence-shaped literals anywhere in a tab. Noisier; skim it. Every violation found so far has
# been an instruction, never a one-word label.
grep -rnE '"[A-Z][a-z]+ [a-z]+ [^"]*"' --include="*.kt" \
  composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/tabs/ \
  | grep -v stringResource

# Non-null assertions
grep -rnE '[A-Za-z0-9_)\]]!!' --include="*.kt" composeApp/src/jvmMain/kotlin/

# Text used as an icon
grep -rnE 'Text\("[^a-zA-Z0-9" %$]{1,2}"' --include="*.kt" composeApp/src/jvmMain/kotlin/

# Unused string resources (keys in values/strings.xml never referenced from Kotlin)
comm -23 \
  <(grep -oE '<(string|plurals|string-array) name="[^"]+"' strings/src/main/composeResources/values/strings.xml | sed -E 's/.*name="//;s/"//' | sort -u) \
  <(git grep -hoE '(Res\.(string|plurals|array)\.|generated\.resources\.)[A-Za-z0-9_]+' -- '*.kt' | sed -E 's/.*\.//' | sort -u)
```

### Audit status

> Last audited: 2026-09-29, whole tree at the `housekeeping` branch.

| Item | Count | Notes |
|------|-------|-------|
| Wildcard imports | 0 | whole repo ✅ |
| Material 2 imports | 0 | whole repo ✅ |
| `println` in `composeApp` | 0 | ✅ |
| `System.err.println` in `composeApp` | 0 | diagnostics go through `Log` (`:diagnostics`) ✅ |
| Fully qualified `androidx.compose.*` in code | 0 | whole repo, excluding imports, `@OptIn` and KDoc ✅ |
| Fully qualified names where the import already exists | 0 | ✅ |
| `!!` in `composeApp` | 0 | ✅ |
| Unused string resources | 0 | ✅ |
| Hardcoded UI strings | known exceptions only | `Text("$w×$h")` resolutions. Remote-activity toasts carry a `RemoteLabel` that the desktop words in the operator's language ✅ |
| Emoji used as icons | pending | the icon maps in `ScheduleItemDisplay`, `RemoteActivityToast` and `BibleTab`'s 📖 — need icon assets |

### Decision log

**Kept intentionally:**
- Fully qualified `java.*` references that have **no** matching import. Many disambiguate against a
  Compose type of the same simple name (`java.awt.Window` vs Compose's `Window`,
  `java.awt.image.BufferedImage`); `java.awt.Toolkit.getDefaultToolkit().systemClipboard` is the
  settled clipboard one-liner. The rule is about the long form where an import already binds the
  name.
- `println` in the CLI tools (`presentation-engine` `DumpKeynote`/`DumpTiming`/`MakeSampleDeck`,
  `bible-engine` `tools/`, `lottieGenerator` `DumpStyleReview`) — printing is their purpose.
- `println` in `bible-engine`'s standalone launch (`Main.kt`, `AppConfig.kt`) and behind its
  `verboseLog` flag — the engine's own console output; the app runs it in-process with the flag
  off. `cleanup_check.sh` skips exactly these and the `tools/` packages.
- Hardcoded `"%"` suffixes on dynamic values — the percent sign is identical in every supported
  locale.

**Not kept:** emoji strings used as icons. AGENT.md forbids them; the remaining icon maps are listed
as pending above.

---

## 🚀 Development Workflow

1. **Plan strings first** — add them to `values/strings.xml`.
2. **Plan constants** — add them to the right `Constants` object.
3. **Write the code** — string resources and constants, explicit imports, Material 3.
4. **Test** — `bash test-changed.sh` in the inner loop, `./gradlew :composeApp:check` before you
   commit.
5. **Clean up** — remove what the change made unused, including string resources.
6. **Verify** — `bash cleanup_check.sh`, then `./gradlew :composeApp:detekt` last.
7. **Commit** — on a branch, with clean, standards-compliant code.

---

## 🛡️ Crash Reporting

Crash reporting lives in the `:diagnostics` module (`CrashReporter`). A global uncaught-exception
handler installed at startup writes each crash — timestamp, app version, OS, Java version, stack
trace — to `~/.churchpresenter/crash-reports/` (`C:\Users\<username>\.churchpresenter\crash-reports\`
on Windows), deletes logs older than 30 days on startup, and forwards a PII-scrubbed event to Sentry.

Report important caught exceptions:

```kotlin
try {
    // risky operation
} catch (e: Exception) {
    CrashReporter.reportException(e, "Loading song file")
    // handle gracefully
}
```

---

## 🤝 Contributing

### Getting started

1. Fork and clone the repository — no submodules, a plain clone is everything.
2. Install JDK 21 (Temurin recommended).
3. Run `./gradlew :composeApp:run` to verify the build works.
4. Read this guide, CODING_STANDARDS.md and AGENT.md before making changes.

### Never commit directly to `main`

`main` is protected on GitHub for everyone, admins included — it takes a pull request with the
`test` check passing. The repo also ships a `pre-commit` hook in `.githooks/` that refuses a commit
made while `main` (or `master`) is checked out. Any `./gradlew` invocation sets `core.hooksPath` for
you; to set it by hand: `git config core.hooksPath .githooks`.

### Running the tests, per platform

The app's own suite is `./gradlew :composeApp:check` and needs only **JDK 21**. In the inner loop,
`bash test-changed.sh` runs only the suites that name what you changed — a heuristic that cannot see
through a symbol three layers down or know a composable moved pixels in an unrelated screenshot
suite, so run the full `check` before you commit.

The suite runs on up to 4 parallel JVMs (~5 min). Tests that bind a port go through `testPort()`,
and the shared fake home is per fork — see AGENT.md, "The suite runs in parallel forks".

| Platform | Status |
|----------|--------|
| **Linux** | What CI runs (`ubuntu-latest`), fully headless. |
| **Windows** | Runs the full suite, including the Compose UI tests. |
| **macOS** | Runs the suite; the canonical platform for the committed screenshot set — record and verify here. |

If Compose UI tests fail with `NoClassDefFoundError: Could not initialize class
org.jetbrains.skia.Surface`, a test faked `os.name` before Compose was first touched — see AGENT.md
(`TestSingletons.latchSkikoHostOs()`). `--rerun-tasks` will not clear it.

### The modules of this build

Every module is part of this one build (see the table in AGENT.md), tested through the root
wrapper:

```bash
./gradlew :converter:test              # song/Bible converter (also :converter:run)
./gradlew :companion-satellite:test    # Companion Satellite protocol client
./gradlew :theme:test                  # color schemes, type and shape scales
./gradlew :core-models:test            # shared data models
./gradlew :bible-engine:test           # Bible Lookup Engine
./gradlew :lottieGenerator:test        # Lottie lower-third generator
./gradlew :crossword:test              # crossword authoring tool (also :crossword:run)
./gradlew :presentation-engine:test    # PPTX/Keynote/PDF engine
./gradlew :songlibrary:test            # Song Library Manager
./gradlew :settings:test               # persisted settings
./gradlew :diagnostics:test            # crash reporting
./gradlew :atem:test                   # ATEM protocol client
./gradlew :ndi:test                    # NDI send and receive
./gradlew :omt:test                    # OMT send and receive
./gradlew :planning-center:test        # Planning Center client
./gradlew :bible-formats:test          # Bible download catalogues and .spb converters
./gradlew :song-chords:test            # chord grammar and transposition
./gradlew :bible:test                  # loaded .spb translations and search
./gradlew :calendar:test               # Calendar Manager
```

`:composeApp:check` does **not** reach any of them. CI runs each as its own step, only when the
change touched that module, a module it depends on (from `./gradlew moduleGraph`) or the shared build
files; `bash test-changed.sh` names the module tasks a change implies.

### DeckLink hardware tests

`DeckLinkHardwareTest` drives a real Blackmagic card and is **opt-in** — opening the output pushes a
frame to whatever the card is wired to. To do a deliberate hardware pass on a machine with a card:

```bash
./gradlew :composeApp:jvmTest -PdecklinkHardware=true --tests '*DeckLinkHardwareTest*'
```

### Pull request guidelines

- Keep PRs focused on a single feature or fix.
- Follow CODING_STANDARDS.md; add string resources for any new UI text.
- Re-record the screenshots of any state you changed (AGENT.md, "Screenshots").
- Run the verification commands before submitting.

### Credential files

**Never commit credential or config files.** The `.gitignore` excludes `firebase-config.json`,
`google-services.json`, `serviceAccountKey.json`, `.env` files and `.p12` certificates. For project
credentials, contact the maintainer directly.
