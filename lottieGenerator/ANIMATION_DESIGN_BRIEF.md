# Design Brief: New Lower-Third Animation Concept

This brief is for proposing the **concept** for a new LottieGen animation style — the visual
look and motion, not the code. It contains no Kotlin, no file paths, no class names. Once a
concept is approved, hand it (plus this repo) to a developer/agent following
`ADDING_ANIMATIONS.md`, which covers how to actually build it.

LottieGen currently ships 12 complete lower-third looks (Bar, Boxed, Circular, Banner, Gradient
Bar, Line Split, Random Fade, Diagonal, Diagonal Wipe, Double Line, News Ticker, News Badge).
A new concept should feel like a genuine 13th addition to that family, not a re-skin of one of
them.

## What you're designing

A **lower-third overlay**: a name + a secondary info line (title, verse reference, whatever the
operator types), optionally a logo, that animates onto a live video feed, holds, then animates
off. Think broadcast news chyrons, worship service name/title cards, conference speaker
credits — that visual register, not a full-screen slide or a corner bug.

## Hard constraints (every concept must satisfy all of these)

- **Arbitrary canvas size.** Default is 1920×1080, but the canvas can be any resolution the
  presenter display uses — the app ships presets from 21:9 ultra-wide (2560×1080) through
  16:9/16:10/4:3 down to square (1080×1080) and 9:16 portrait (1080×1920). All sizing scales
  off canvas *height*, so the composition must stay sane when the frame is much narrower than
  16:9 — portrait is the stress test. The design must scale proportionally, not assume a fixed
  pixel size.
- **Lives near the bottom of the frame**, inset from the edges by a configurable safe margin
  (both horizontal and vertical) — never bleed off-canvas or ignore the margin. This applies to
  *every* element you propose, including decorative/ambient motifs (particles, snow, confetti,
  sparkles, etc.) — those stay bounded to the lower-third composition too. Nothing in a concept
  covers or animates across the full frame; this is a lower-third overlay, not a full-screen
  scene.
- **Three alignment modes: left, center, right.** The operator picks one. Your concept needs a
  coherent answer for how the whole composition mirrors/re-centers for each — which direction
  things enter from, how text justifies, where any accent shape sits relative to the text.
- **Arbitrary text length.** "Jane Smith" and "The Right Reverend Alexander Cunningham-Whitfield
  III" must both look intentional, not overflow or collide with other elements. Don't design
  around a specific example string.
- **Arbitrary colors, chosen per-field by the end user** (name text color, info text color, an
  "accent" color, a background color, a border color) — there is no fixed brand palette to
  design against. The concept should read well across light-on-dark, dark-on-light, and
  saturated-accent-on-neutral combinations, not just whatever example colors you sketch it with.
- **Every color also carries its own 0–100% opacity**, set by the operator. The concept must
  survive semi-transparent fills — e.g. a 40%-alpha background panel over busy video — not
  assume solid paint anywhere.
- **Optional elements**: a logo image may or may not be present; the background fill may be
  disabled (transparent); either the name or the info line may be hidden entirely (e.g. name
  only, no title). The composition must still make sense with any of these switched off — not
  just leave a gap.
- **Logo placement is the concept's job.** The operator only toggles the logo on/off and sets
  its size (roughly 2–6× the base text size); there is no position control. The concept must
  say where the logo sits — and how that changes per alignment. Omitting the logo entirely at
  center alignment is acceptable and has precedent in existing styles.
- **The canvas has no inherent color.** It is composited transparently over live video/graphics
  — never assume or paint an opaque full-canvas fill (white or any other solid color) as part of
  a concept. "Background" in this brief always means the optional panel sized to and sitting
  directly behind the lower-third composition itself (the thing `bgEnabled` toggles above) — not
  a canvas-filling wash. A concept must look correct with that panel disabled, i.e. genuinely
  transparent everywhere outside the composition's own bounds.
- **One font family, per-line weight and case.** The operator picks a single font family for
  the whole composition; weight (bold/regular) and an uppercase transform are then chosen
  separately for the name vs. info line. Don't design something that requires two different
  typefaces — but do make sure the design still reads when the two lines differ in weight and
  case.
- **Relative text sizes are adjustable.** The name and info line each have an operator-set size
  multiplier (defaults are roughly 1.2× and 0.9× of a shared base size) — don't design a lockup
  that only works at one specific name-to-info size ratio.
- **Border is a thickness slider where 0 means no border at all.** A concept may include
  border-only accent elements (existing styles do — an accent line that simply doesn't exist at
  thickness 0), but the composition must look complete with borders absent.
- **Corner radius is operator-adjustable**, from fully sharp to visibly rounded — panels,
  badges and bars must read well at both extremes.

## Timing budget

- Default: **~4 seconds animate-in, ~3 seconds hold, then animate-out** (this is adjustable by
  the operator, but design against these defaults).
- **The exit is mechanically the reverse of the entrance** — the same motion played backward in
  time. Whatever you design for "in," picture it running in reverse for "out," and make sure that
  reads as a sensible exit, not just an accident of the entrance looking OK forwards only.
  (A wipe that reveals left-to-right will withdraw right-to-left; a panel that scales up will
  scale back down; design accordingly rather than proposing separate bespoke exit choreography.)

## Motion palette (what's implementable)

Concepts must be buildable from these primitives — anything outside them won't survive handoff:

- **Whole-element motion**: slide/translate, scale (uniform or squash/stretch), rotate, fade
  (opacity) — rotation and scale may orbit a chosen pivot point (e.g. swing in from a corner).
- **Reveals**: rectangular or polygonal clip/wipe masks (including diagonal edges).
- **Curved shapes & draw-on**: bezier-curved lines/shapes whose stroke can progressively draw
  itself along the path (and retract on exit) — vines, flourishes, underline sweeps. Strokes
  may be dashed for dotted draw-ons.
- **Repeated copies**: a shape can repeat as N evenly offset/rotated/scaled copies, optionally
  fading out — rows of leaves, dot trails, sparkle runs (still bounded to the composition).
- **Per-character text effects**: exactly two — a sequential letter-by-letter reveal from an
  edge, and a randomized per-letter fade.
- **Easing**: one shared soft ease curve plus linear. There are **no springs, no
  overshoot/bounce physics, no motion blur, and no 3D** — don't storyboard around them.

## Existing motion vocabulary (for contrast — don't reuse these as your headline idea)

All 12 shipped looks, so you can check a new idea against every one of them:

- **Bar**: text slides in from off-screen behind a solid accent bar; background rectangle grows
  underneath.
- **Boxed**: two stacked pill-shaped boxes (accent-colored name box, background-colored info box)
  each expand vertically from zero height while masked text slides in; a square logo badge scales
  up from nothing.
- **Circular**: a rounded-square logo badge scales up while counter-rotating into place; the name
  slides up and the info slides down into masked windows; a background bar then expands
  horizontally out from the badge.
- **Banner**: stacked bars expand their width from zero, staggered in time; a small accent block
  scales in; a top accent line wipes across (only when a border is enabled).
- **Gradient Bar**: edge-faded gradient bars slide in from off-canvas; masked name/info text
  slides the full canvas width into place.
- **Line Split**: individual letters pop up (name) / pop down (info) from a horizontal dividing
  line that expands from the alignment edge or center.
- **Random Fade**: individual letters fade in at randomized moments (not left-to-right order).
- **Diagonal**: a slanted parallelogram band slides up from below the frame; the text rides up
  with it and reveals letter-by-letter (the slant straightens out at center alignment).
- **Diagonal Wipe**: a diagonal mask sweeps across to reveal accent-colored text, with a second,
  separately-revealed "normal" text layer underneath.
- **Double Line**: two horizontal accent lines expand from the alignment edge; the name slides up
  and the info slides down from behind the lower line.
- **News Ticker**: the whole band slides up from below the frame; name and info text are each
  clipped to their own band and slide in from opposite edges.
- **News Badge**: a diagonal parallelogram badge with slash dividers; name/info again clipped and
  sliding from opposite sides.

## What a good concept proposal looks like

A plain-language storyboard, not code:

1. **List the elements**: e.g. "an accent-colored ribbon shape, the name text, the info text, an
   optional logo, a background panel." Confirm every element listed — including any
   decorative/ambient one — is bounded to the lower-third composition, not a full-canvas layer;
   there is no opaque canvas to paint on.
2. **Describe the entrance choreography**: what appears first, what follows and after how much
   delay (as a rough fraction of the ~4s in-animation, e.g. "background panel expands over the
   first 40%, then text slides in over the remaining 60%"), and the direction/manner each element
   moves or reveals (slide, wipe, scale, fade, rotate, clip-reveal, etc.).
3. **Describe the hold state**: the hold is normally static — the completed in-state simply
   freezes. Subtle hold motion (a slow pulse, a looping shimmer) is possible but costs bespoke
   extra work, so only propose it if it's core to the idea, and say so explicitly.
4. **State how it adapts per alignment** (left/center/right) — which parts mirror, which stay
   fixed, which direction reverses.
5. **Name what makes it distinct** from the 12 existing looks and from the vocabulary list above.

Keep it to a few paragraphs or a short numbered storyboard — the goal is a clear creative
direction a developer can implement deterministically, not a mockup or pixel-perfect spec.

## Handoff

Once a concept is approved, give it — along with this repo's `ADDING_ANIMATIONS.md` — to
whoever (or whichever agent) implements it. That document covers the actual Kotlin
architecture, the config fields a style must respect, and the registration steps needed for the
new style to appear in the app.
