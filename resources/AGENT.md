# `:resources` — Agent Notes

Rules and structure for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**Every asset the app draws or reads**: the icons, the interface strings in all 35 languages, the
fonts, and the bundled files (sample songs and bibles, backgrounds, the EULA and licence, the
crossword puzzles). A real Gradle module of this build: `include(":resources")`,
`api(projects.resources)`.

| Directory | Holds |
|---|---|
| `drawable/` | 61 icons — 59 vector drawables and 2 raster |
| `values/` + 34 `values-*/` | `strings.xml`, one per supported language |
| `font/` | The bundled typefaces |
| `files/` | Sample songs and bibles, backgrounds, `eula.txt`, `gpl3.txt`, and `crossword/` |

## Rules

- **There is exactly one `Res`, and that is the whole point.** The accessor is generated into
  `org.churchpresenter.resources.generated.resources` with `publicResClass = true`, so every
  consumer writes `Res.drawable.ic_close` and `Res.string.save` exactly as it did when these lived
  in `:composeApp`. Splitting icons and strings into two modules would mean two classes called
  `Res` — and 46 files use both, so every one of them would need an import alias at every call
  site. That was tried and reverted; do not split them again.
- **`api`, not `implementation`, on the consumer side.** A `Res` reference appears in `:composeApp`'s
  own public composable signatures via `painterResource`/`stringResource`, so the type has to stay
  on the compile classpath of anything that depends on it.
- **No detekt, no jacoco, no test source set, deliberately.** This module holds assets and the
  accessor generated from them, and no Kotlin of its own. The root build only wires those gates in
  for a module that applies the `detekt` and `jacoco` plugins, so leaving both off is what keeps
  them honest — a coverage floor over generated code measures nothing. **Do not add Kotlin here.**
  If something needs logic, it belongs in the module that uses it.
- **NEVER touch a non-English locale file.** The repo-wide rule in the root `AGENT.md` applies with
  full force here, because this is where all 34 of them now live.
- **New user-facing strings go in `values/strings.xml`** — the English one, and only that one.

## The crossword sync

`syncCrosswordFiles` copies `crossword/encoded/*.xwp` into `files/crossword/` and runs ahead of
resource processing. It lives here rather than in `:composeApp` because this module owns `files/`
now. Edit the puzzles in the `:crossword` module, then rebuild.

## Commands

There is no suite and no gate to run. The assets are proved by `:composeApp:check` — a missing or
unreadable one fails the screenshot tests, which render the real UI against them.
