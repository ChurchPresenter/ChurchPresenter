# `:detekt-rules` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`.

## What it is

The repo's own detekt rules, in the rule set `churchpresenter`. The root `build.gradle.kts` adds
this module to `detektPlugins` of every module that applies detekt, and `config/detekt/detekt.yml`
configures the rules under `churchpresenter:`.

| Rule             | Flags                                                                                     |
|------------------|-------------------------------------------------------------------------------------------|
| `HardcodedColor` | `Color(…)` built only from literals, and named constants (`Color.Red`, `Color.WHITE`, …) |

`HardcodedColor` passes `Color.Transparent`, `Color.Unspecified`, any `Color(…)` with a
non-literal argument, any parameter's default value, and a projection background: `Color.Black`
(or its `.copy(…)`) handed to `background(…)`, a `background`/`screenColor` argument, a
`…Background(color = …)` call, or a property named `…background…`/`…Bg…`. It is excluded from `:theme` (the palette), files named `*ColorPicker*.kt`,
and test sources.

## Rules

- **The stdlib is pinned to detekt's** (`coreLibrariesVersion`) — the rules run inside detekt's
  embedded compiler, not this build's Kotlin. Use no stdlib API newer than that version.
- **PSI only, no type resolution** — the modules run plain `detekt`, not `detektMain`, so a rule
  matches on names, never on resolved types.
- detekt does not run on this module itself (it would have to load itself as a plugin).

## Commands

```bash
./gradlew :detekt-rules:build
./gradlew detekt --continue   # every module, with these rules loaded
```
