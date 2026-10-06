# `:detekt-rules` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The project's own detekt rules, in the rule set `churchpresenter`. Not a dependency of any module:
the root `build.gradle.kts` adds it to every module's `detektPlugins`, and its settings live in the
shared `config/detekt/detekt.yml`.

- **`HardcodedString`** — every string literal is a `const val` or a string resource. Allowed: a
  `const val` initializer, an annotation argument, an argument to a call in `allowedCalls` (`Log.info`,
  `Log.warn`, `Log.error`). Ignored: `""` and templates made only of interpolations and whitespace.
  Test sources are excluded in the config, not in the rule.

## Rolling out `HardcodedString`

It lands **switched off** (`active: false`) because it finds ~14,800 literals across the modules.
It is turned on module by module:

1. Measure: `./gradlew :<module>:detekt -PhardcodedStrings` switches it on everywhere and reports
   without failing (`config/detekt/hardcoded-strings.yml`).
2. Clean the module: user-facing text into `:strings` (English `values/strings.xml` only), everything
   else into a `const val`.
3. Opt in: in `config/detekt/detekt.yml` set `active: true` and add the module's source root to
   `includes`, e.g. `'**/theme/src/main/**'`. With `active: true`, an empty `includes` means every
   module — only empty it once all are clean.

**Never silence a finding** with `@Suppress("HardcodedString")`, a baseline entry, a wider `excludes`
or a new `allowedCalls` entry without asking first — the same rule as the coverage excludes.

## Rules

- **Code here runs on detekt's Kotlin runtime (2.0.21), not the build's.** Stick to stdlib calls that
  exist in Kotlin 2.0; a newer one compiles and then fails with `NoSuchMethodError` inside detekt.
- PSI only — no type resolution. Rules must decide from syntax.
- A new rule: add it to `ChurchPresenterRuleSetProvider`, give it a section under `churchpresenter:`
  in `config/detekt/detekt.yml` (detekt rejects an unknown rule), and test it with `lint()` from
  `detekt-test`.

## Commands

```bash
./gradlew :detekt-rules:test :detekt-rules:jacocoTestCoverageVerification :detekt-rules:detekt
./gradlew :<module>:detekt -PhardcodedStrings   # report HardcodedString findings, never fails
```
