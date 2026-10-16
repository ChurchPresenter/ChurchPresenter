# `:detekt-rules` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The build's own detekt rules**, as the `live-safe` rule set: `BlockingCallOnEventThread`, which
keeps file, network, decode and sleep calls out of composition on the output paths (root AGENT.md,
"Live-safe output paths"). A real Gradle module of this build, `include(":detekt-rules")`, on no
classpath of the app: `cp.detekt` hands its jar to every module's detekt as a plugin, not
transitively, and the module lints itself with its own jar.

## Layout

| Path | Owns |
|---|---|
| `BlockingCallOnEventThread.kt` | The rule: which call names block, which functions are composables, which lambdas leave the event thread |
| `LiveSafeAllowlist.kt` | Reading `config/architecture/live-safe-allowed.txt`, found in the nearest directory above the file being linted |
| `LiveSafeRuleSetProvider.kt` | The `live-safe` rule set, registered in `META-INF/services` |
| `resources/config/config.yml` | The rule set's defaults, so detekt's config validation knows its keys |

## Rules

- **detekt runs this jar beside its own, older Kotlin standard library.** The module compiles
  against Kotlin API 2.2 so a newer library call fails here, not inside a detekt run.
- **The rule reads names, not types**: there is no type resolution in this build's detekt. Keep a
  new name specific enough that it rarely means something else; a false hit costs an allowlist line.
- **Which modules it checks is `includes:` in `config/detekt/detekt.yml`**, not code here.
- detekt's class loader outlives a build in the Gradle daemon, so anything cached across files is
  keyed on what it read (the allowlist is cached on its path, size and time).
- **Never add a line to the allowlist without asking.**

## Commands

```bash
./gradlew :detekt-rules:test :detekt-rules:detekt
./gradlew :live-output:detekt          # the rule on one output module
./gradlew checkLiveSafeAllowlist       # every allowlist line names a file that exists
```
