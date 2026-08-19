# Adding a New LottieGen Animation Style

Technical implementation checklist for turning an approved animation concept (see
`ANIMATION_DESIGN_BRIEF.md`) into a working style. There is no entrance/exit/emphasis taxonomy in
this codebase — each "style" is a complete, numbered look: its own layout plus its own in→hold→out
choreography for every layer it owns.

There are two ways to build a style:

- **Path A — hand-written Kotlin generator** (how styles 1–12 are built): write one generator
  class and wire it into four small registration points. Full power, needs a rebuild per
  iteration. Covered by the rest of this document.
- **Path B — spec-based style via the Animation Style Editor** (developer tool, July 2026):
  design the style as a declarative JSON spec with instant live preview, then register the spec.
  See the "Path B" section at the end. Prefer Path B unless the concept needs geometry or
  choreography the spec model can't express — Path A remains fully supported as the escape hatch.

Read `src/main/kotlin/lottiegen/lottie/styles/Style1Bar.kt` first — it's the most representative
existing style (logo + masked text reveal + accent bar + background) and covers most of the
patterns below.

## Architecture (read before writing code)

- **One class per style**, implementing `lottie/styles/StyleGenerator.kt`:
  ```kotlin
  interface StyleGenerator {
      fun generate(builder: LottieBuilder, cfg: LottieGenConfig)
  }
  ```
  `builder` already has duration set; `cfg.baseSize` is already scaled by
  `(canvasH / 1080) * scaleFactor` — never rescale it again.
- **Dispatch**: `lottie/LottieGenerator.kt` holds `Map<String, StyleGenerator>` keyed by numeric
  string id ("1".."12"). It computes `fr = 60`, `inFrames`/`holdFrames`/`outFrames` from
  `cfg.animDuration`/`holdDuration` (seconds → frames at 60fps), builds the `LottieBuilder`, calls
  `styles[cfg.style].generate(builder, cfg)`, then `builder.toJson()`.
- **Keyframes — supply ONLY the "in" curve.** `lottie/KeyframeUtils.kt`'s
  `buildKeyframes(inKFs: List<KeyframeInput>, inFrames, holdFrames, outFrames, easing)` takes
  keyframes as `(percent 0-100, JsonElement value)` pairs describing the animate-in motion only;
  it automatically converts percent→frame, adds a hold keyframe, and mirrors the reverse for
  animate-out. Never hand-write hold/out keyframes or raw bezier tangents — always go through
  this function.
- **Easing**: exactly two shared curves in `KeyframeUtils.kt`'s `Easing` object —
  `Easing.DEFAULT` (soft cubic-bezier, the default for nearly everything) and `Easing.LINEAR`.
  Reuse one of these; don't invent a new tangent pair.
- **Sizing**: `emToPx(em, baseSizePx)` / `remToPx(rem, baseSizePx)` (`lottie/ColorUtils.kt`)
  convert relative units against `cfg.baseSize` — this is what keeps a style resolution- and
  scale-independent across canvas sizes. Never use a hardcoded pixel literal for layout;
  everything (bar widths, margins, corner radii, logo size, text margins) should be
  `emToPx(N, baseSize)` or `remToPx(N, baseSize)`.
- **Shape/text construction**: only build layer content via the existing helpers —
  `lottie/ShapeHelpers.kt` (`makeRect`, `makeAnimatedRect`, `makeEllipse`, `makePath`, `makeFill`,
  `makeGradientFill`, `makeStroke`/`makeAnimatedStroke`, `makeGroup`) and `lottie/TextHelpers.kt`
  (`makeTextData`, `makeTextDataWithAnimators` + `makeTextRevealAnimator`/`makeRandomFadeAnimator`
  for per-character effects). Register the results as layers via
  `LottieBuilder.addShapeLayer/addTextLayer/addImageLayer` and build transforms via
  `LottieBuilder.defaultTransform(opacity/rotation/position/anchor/scale)` +
  `LottieBuilder.animatedProp(keyframes)` / `staticProp(value)` / `staticPropArray(...)`. Do not
  construct raw `JsonObject` layers by hand.
- **Text reveal via mask**: the standard way to clip/reveal a text layer is a same-position
  `"<Field> Mask"` shape layer (a filled rect sized to the text, `td = 1`) added immediately
  before the text layer, with the text layer given `tt = 1` to matte against it. See
  `Style1Bar.kt`'s "Name Mask" + "Name" pair for the full pattern (mask shape, position, then
  `addFont` + text keyframes + `addTextLayer(..., tt = 1)`).
- **Fonts**: call `builder.addFont(family, weight)` before building text data for that field — it
  registers the font (dedup'd by name) and returns the resolved `"Family-Bold"/"Family-Regular"`
  name that `makeTextData` computes internally the same way.

## Design requirements — what makes a style "fit"

A new style must honor the full `LottieGenConfig` surface the other 12 do, or it will feel
inconsistent/broken next to them in the picker:

- **Alignment** (`cfg.align`: `"left"`/`"center"`/`"right"`) — drives text justify (`0`/`2`/`1`)
  and which direction elements slide in from. Study `Style1Bar.kt`'s `isRight`/`isCenter`
  branching for base position, bar position, and slide-offset sign.
- **Visibility toggles**: `hideName`, `hideInfo`, `logoEnabled`, `bgEnabled` — each must actually
  skip building/adding that layer, not just hide it, when false.
- **Colors + alpha**: `nameColor`/`infoColor`/`accentColor`/`bgColor`/`borderColor`, each with a
  matching `*ColorAlpha` (0-100) — convert hex via `hexToLottie(...)` and pass alpha into
  `makeFill`/`makeAnimatedStroke`.
- **Shape**: `corners` (corner radius, via `emToPx`), `borderThickness` (0 = no border/stroke).
- **Text style**: `nameTransform`/`infoTransform` (`"uppercase"`/`"none"`, handled inside
  `makeTextData` — don't re-implement), `nameWeight`/`infoWeight` (Bold vs Regular cutoff at 700).
- **Timing**: never hardcode frame counts — always derive from `builder.inFrames`/`holdFrames`/
  `outFrames`, which already reflect `cfg.animDuration`/`holdDuration`.
- **Layer naming** (`nm` field): follow the existing convention — `"Logo"`, `"<Field> Mask"`,
  `"<Field>"` (e.g. `"Name"`, `"Info"`), a descriptive shape name (`"Accent Bar"`,
  `"Gradient Bar"`, etc.), `"Background"`. These aren't parsed anywhere outside this module,
  but keep them readable for anyone opening the JSON later.

## Step-by-step registration checklist

Do these in order — a style that's only partially registered will either not compile or won't
appear in the picker:

1. **Write the generator**: new file `lottie/styles/StyleNXxx.kt` (pick the next sequential
   number and a short descriptive name, e.g. `Style13Ribbon.kt`), implementing
   `StyleGenerator.generate(builder, cfg)` per the architecture/design sections above.
2. **Register in the dispatch map**: add `"N" to StyleNXxx()` to the `styles` map in
   `lottie/LottieGenerator.kt` (currently lines 24-37, ids `"1"`..`"12"`), plus the import.
3. **Add the enum constant**: in `model/Enums.kt`'s `AnimationStyle` enum, add
   `STYLE_N("N", "style_N")` and a matching branch in the `label` `when` block
   (`STYLE_N -> Strings.styleN`).
4. **Add the Strings getter**: in `ui/Strings.kt`, add
   `val styleN: String get() = bundle.getString("style_N")` alongside the existing `style1`..
   `style12` getters.
5. **Add the label — base properties file ONLY**: add `style_N=Style N — <Name>` to
   `src/main/resources/lottiegen_strings.properties` (follow the existing `Style N — <Name>`
   format, e.g. `style_13=Style 13 — Ribbon`).
   **Do NOT add or edit `_be`/`_cs`/`_de`/`_kk`/`_pl`/`_ru`/`_uk` variants** — never translate
   unless explicitly asked, and it's safe to leave them alone because `ResourceBundle.getBundle`
   (`ui/Strings.kt`) automatically falls back to the base bundle for any key missing from a
   locale-specific file. The new style will show its English label in every locale until someone
   explicitly requests translations.
6. **No UI change needed**: `ui/ControlPanel.kt`'s style dropdown iterates `AnimationStyle.entries`
   automatically — a new enum constant appears in the picker for free.

## Verification

There are no automated tests in this module (no `src/test` directory) — the live preview
panel is the actual acceptance gate:

1. Compile: `./gradlew compileKotlinJvm` (from the main ChurchPresenter repo root, since this
   module's Kotlin is mounted into the main build) or `./gradlew build` from this module's
   own root if run standalone.
2. Run the app (`./gradlew run` from this module, or the Lower Third settings screen inside
   the main ChurchPresenter app), select the new style in the dropdown, and check the live preview
   across:
   - All three alignments (left/center/right)
   - Logo on and off
   - Background on and off
   - `hideName`/`hideInfo` toggled independently
   - A light-on-dark and a dark-on-light color combination (to catch any hardcoded color
     assumption)
3. Confirm the in→hold→out lifecycle looks right at both a short and long `animDuration`/
   `holdDuration` — since out is a mirror of in, a motion that only looks right playing forward
   (e.g. a linear wipe with an asymmetric feel) may look wrong reversed on exit.

## Path B — Spec-based styles (Animation Style Editor)

A style can instead be a declarative JSON document (`lottiegen.spec.StyleSpec`) rendered by the
generic `SpecStyleGenerator` — the same LottieBuilder/`buildKeyframes` pipeline underneath, so
spec styles honor every config field (alignment, colors+alphas, visibility toggles, border,
corners, logo, timing) by construction. The developer Style Editor authors these specs visually.

### Authoring

1. Open the editor: `./gradlew run --args="--editor"` from this repo, or
   **Developer → Animation Style Editor…** inside ChurchPresenter (Developer menu appears in dev
   builds or with `CHURCHPRESENTER_FORCE_DEV_WINDOW=true`).
2. Start from a template. Besides the classic ports (below), six **starter designs** each
   demonstrate one of the newer primitives: Frame Draw (closed-rect trim outline),
   Underline Flourish (curved trim + per-character reveal), Swing Sign (pivot rotation),
   Marquee (dashed border + repeater bulbs), Heartbeat (polyline trim, linear easing),
   Sparkle Pop (x/y squash scale + fading repeater) — plus the vine demo (curve + trim +
   staggered pivoted leaves). Fifteen **church-themed designs** join them: Cross Draw-On and
   Ichthys Fish (staggered stroke trim symbols), Radiant Light Rays (rotating repeater fan
   behind the text), Stained Glass (translucent role-tinted panes via `alphaFactor`),
   Christmas Star (scale + rotation settle with twinkles), Easter Dawn (rising sun, cross
   silhouette, text-fitted horizon line), Open Bible (page outlines + verse lines), Descending
   Dove (trim draw-on while drifting in), Candle (pivot-anchored grow-up + flame pop + glow),
   Church Steeple and Communion Chalice and Crown (filled silhouette polygons with staged
   accents), Worship Notes (text-fitted 5-line staff via a y-offset repeat + popping notes),
   Flame (FILLED bezier paths with squash overshoot), Shepherd's Staff (crook
   trim draw-on). **All 12 classic styles are ported as editable specs**
   (`src/main/resources/styles/style<N>_*_port.json`, listed in the New dialog by their style
   labels) plus the vine demo, or a blank spec. Each port is verified against its compiled
   original by a `SpecPort<N>Test` (layer structure exact in every config; rest geometry exact
   except deviations documented in each test's KDoc — mostly cfg-derived constants baked for
   the default config). The compiled classes remain what users get for ids 1–12; the ports are
   editing templates. Draft projects live in
   `~/.churchpresenter/churchpresenter-lottiegen/style-specs/` (Save/Save As/Open in the toolbar).
3. Design with the element list + inspector (left), the live preview + timeline scrub (right),
   and the **Test matrix** mode (3 alignments × logo × background at the end-of-in frame). The
   test-configuration section drives the sample operator config, including canvas presets and
   light-on-dark/dark-on-light color checks.
4. Spec concepts worth knowing: the content block is a run of **slots** (LOGO/FIXED/TEXT) laid
   out from the alignment edge inward; placements use flow coordinates (+x = inward,
   auto-mirrored for right alignment) with per-alignment overrides; paints reference color
   **roles**, never literal colors; animation tracks describe the animate-in only (the exit is
   the automatic mirror); text elements support the mask-reveal pattern and the two
   per-character animators.
5. Organic/draw-on primitives (July 2026, all Compottie-verified): **curved path elements**
   (bezier vertices with tangent handles; the flow sign flips tangents too, so curves mirror
   correctly on right alignment) grown via a **TRIM track** ([startFrac, endFrac] of the path
   length — the vine/draw-on effect, and the mirrored exit retracts it); **pivot** placement
   fields (rotation/scale orbit a chosen point — swing from an edge); **dashed strokes**
   (`dashEm` on any stroke; pairs with TRIM for dotted draw-ons); **Repeat copies** on shape
   elements (N offset/rotated/scaled copies with optional fade-out — leaf rows, dot trails);
   SCALE tracks accept [sx, sy] for squash/stretch. See the bundled **vine demo** template
   (New → From vine demo, `src/main/resources/styles/demo_vine.json`) which exercises all of it.
   **Width fitting** (`fitWidthTo` — Name width / Info width / Widest text line): on paths and
   polygons it scales x-coordinates (and tangents) so the shape spans the measured text instead
   of a fixed em width (underlines, pulse lines); on a Repeat it derives the copy x-offset so
   the whole row spans the basis width (bulb rows that hug the panel at any text length).
   Fixed-width decorations overflow the background panel for short names — prefer a fit basis
   for anything meant to run along the text.
   **Image elements** (July 2026): Add element → Image, then Import a PNG/JPEG — the image is
   downscaled to ≤1920 px and EMBEDDED in the spec as a base64 data URI, so the style ships
   self-contained (keep sources modest; the payload rides in every export). Sizing uses the
   normal SizeSpec box with a scale mode (Fit / Stretch / Cover — Cover crops via a td/tt
   matte), optional rounded-corner clipping, and an opacity factor for watermarks/textures.
   Panel background image recipe: `size: contentDerived` + `corner: fromConfig`, placed just
   above the Background element in the layer list (over the panel, under text).

### Registration (spec styles) — no code edits

1. Toolbar **Export** — set the numeric style id and style name (the dialog lists what's
   registered, previews the picker label, and shows a "Replaces …" notice when you reuse an
   existing id), fix any validation errors.
2. Click **Register into build** (available whenever the editor runs from a source checkout —
   standalone or embedded dev run). It writes `styleN_<slug>.json` into
   `src/main/resources/styles/` AND upserts `styles/registry.json`.
3. **Commit the two files.** That's it — `LottieGenerator` overlays the registry onto the
   compiled styles at startup and the picker (`StyleCatalog`) lists registry ids
   automatically, so the style ships in the next build with zero Kotlin changes. The picker
   label is "Style N — <name>" from the spec; optionally add a `style_N` key to the base
   `lottiegen_strings.properties` to localize it (the catalog prefers the bundle key).
   A corrupt/missing registry spec is skipped at startup with a stderr note — it never breaks
   the other styles.
4. In a packaged build (no source checkout) the **Choose location…** manual export remains;
   move the file into the repo and repeat step 2 from a dev run, or hand-edit registry.json.
5. **Verify**: same acceptance checklist as Path A above; the editor's test matrix covers most
   of it pre-registration.

### Replacing an existing style with a spec

Same flow with the EXISTING style's id: a registry entry for a compiled id (1–12) replaces
that style's renderer — still zero code edits. This changes what every user gets for that
style — hold it to the port fidelity bar (compare against the compiled original across the
alignment/toggle matrix like the `SpecPortNTest`s do) before shipping. The compiled
`StyleNXxx.kt` class stays as the fallback (it renders again if the registry entry is ever
removed); only delete it once the spec version has proven itself.

Spec format notes: field names are frozen once a spec ships in a release (readers use
`ignoreUnknownKeys` + `formatVersion`, currently 1); serialization goes through
`lottiegen.spec.SpecJson` everywhere. Unit/fidelity tests for the spec pipeline live in this
repo's `src/test/` (run `./gradlew test` from this repo root — they are not part of the main
app build, which mounts `src/main` only).

Known spec-model limits (found while porting the 12 classics — the escape hatch is Path A):
no negated visibility rules (NAME_HIDDEN etc. — hide-toggle-reactive layouts approximate),
no config-derived lengths (sizes/offsets can't reference borderThickness/lineSpacing/logoSize),
stroke `FromConfig` hardcodes the 0.1 factor, logo scaling normalizes by max(w,h) (some
classics fit height), text justify always derives from alignment, masks anchor to their own
text extent, one fill + one offset-unit per element. Recurring workaround: per-alignment
element variants — duplicate same-named elements with `PlacementOverride.hidden` so exactly
one builds per alignment.
