# Design note: the layer model

Status: **approved** (roadmap step 1.1). The migration below is roadmap step 2.1 onward.

## Why

Today an output shows exactly **one** thing. `PresenterManager` holds a single
`presentingMode: Presenting` (`NONE, BIBLE, LYRICS, PICTURES, PRESENTATION, MEDIA, LOWER_THIRD,
ANNOUNCEMENTS, WEBSITE, CANVAS, QA, STT, DICTIONARY`), and `PresenterModeContent` dispatches on it.
That one value is read in 135 places across 57 files and set from 31.

What follows from it, as an operator sees it:

- **Putting anything up takes everything else down.** A countdown replaces the song; a lower third
  replaces the verse; a nursery message has nowhere to go.
- **Backgrounds belong to content types.** Each presenter draws its own (`showBibleBackground`,
  `showSongsBackground`, the per-type `BackgroundConfig`s), so changing from a verse to a song can
  change or flash the background even when both should sit on the same motion loop.
- **Clearing is all or nothing.** `requestClearDisplay()` fades everything to `NONE`.
- **No preview before air.** The tabs go live directly; the preview panel shows what *is* live.
- **Per-output differences are a patchwork.** `OutputProfile` has thirteen `show*` flags gating
  content per type (`showsContentFor`), and `screenLocks` pins a screen to one mode.

## The model

### Layers

Every output composes the same fixed stack, bottom to top:

| # | Layer | Holds | Replaces today |
|---|---|---|---|
| 1 | **Background** | Colour, gradient, picture, looping video, camera, NDI/OMT input | The per-content backgrounds |
| 2 | **Media** | A playing video or a picture slideshow, full frame | `MEDIA`, `PICTURES` |
| 3 | **Slide** | One "slide" of content: song section, Bible verses, presentation slide, canvas scene, web page, Q&A question, dictionary entry | `LYRICS`, `BIBLE`, `PRESENTATION`, `CANVAS`, `WEBSITE`, `QA`, `DICTIONARY` |
| 4 | **Captions** | Live transcription | `STT` |
| 5 | **Graphics** | Lower third (Lottie), props (logo bug, clock, live badge) | `LOWER_THIRD` |
| 6 | **Announcements** | Scrolling or static announcement, countdown | `ANNOUNCEMENTS` |
| 7 | **Messages** | Operator text, e.g. a nursery call. Going live with one clears every other layer, as an announcement does today (decision 7) | — (new) |
| — | **Audio** | Audio-only media, not drawn | Audio files under `MEDIA` |

Rules:

- **Each layer holds at most one cue** and is independent: setting the Slide layer leaves Graphics,
  Announcements and Messages alone.
- **Each layer has its own transition** (cut, fade, and the content's own animations) and its own
  **Clear**. Clear All clears 2–7 and keeps the Background unless asked.
- **Captions** have their own layer, so they run over a song, a verse or a video without
  replacing it.

### Cues

A **cue** is what a layer shows: a typed value naming its content and its own state.

```kotlin
sealed interface Cue {
    val layer: Layer
    data class Song(val section: LyricSection, val all: List<LyricSection>, val index: Int, ...) : Cue
    data class Verses(val verses: List<SelectedVerse>) : Cue
    data class Slide(val deck: DeckRef, val index: Int) : Cue
    data class Picture(val folder: String, val index: Int) : Cue
    data class Video(val source: MediaRef) : Cue
    data class LowerThird(val file: String, ...) : Cue
    data class Announcement(val text: String, ...) : Cue
    data class Message(val text: String) : Cue
    data class Background(val config: BackgroundConfig) : Cue
    data class Captions(...) : Cue
    // Scene, Web, Question, Dictionary
}
```

The fields are what the `Live*` parts hold today: `LiveSongs` becomes the state behind a `Song`
cue, `LiveBible` behind `Verses`, and so on. What changes is that they stop being peers that compete
for one mode and become the state of whichever cue is on a layer.

### Live state

```kotlin
class LiveShow {
    val program: State<Map<Layer, Cue>>   // what every output draws
    val preview: State<Map<Layer, Cue>>   // what is cued, not yet on air
    fun take(layer: Layer? = null)        // preview -> program, with that layer's transition
    fun set(cue: Cue)                     // straight to program (direct Go Live)
    fun cue(cue: Cue)                     // to preview
    fun clear(layer: Layer)
    fun clearAll(keepBackground: Boolean = true)
}
```

`PresenterManager` becomes a thin façade over `LiveShow` during the migration (below), then goes.
Its parts stay as they are, as the per-cue state holders.

### Preview / Program

- **Direct Go Live stays the default.** Today's behaviour is `set(cue)`; nobody's workflow changes
  on upgrade.
- **Preview mode is a preference.** With it on, Go Live in a tab means `cue(...)`; the preview panel
  shows Preview beside Program; **Take** (button, key, remote command) moves it across.
- Take moves **every layer that differs**, each with its own transition, so a new song and a new
  background go up together.

### Looks

A **look** is what an output shows of the stack: which layers it draws, and the styling it applies.

- The thirteen `show*` flags on `OutputProfile` collapse into a **layer set** plus a few per-cue
  filters that genuinely differ by content (e.g. a stage screen showing songs but not verses).
- A confidence screen's look drops Background and Media; a stream key's drops Background.
- `screenLocks` becomes "this output follows a fixed look" rather than "this output is pinned to a
  mode".
- Styling (fonts, sizes, positions) stays on the profile in this phase. Moving it into themes is
  Phase 2 and is out of scope here.

### Rendering

`PresenterModeContent`'s `when (mode)` becomes a `Box` drawing each layer the look includes, each
inside its own transition container:

```kotlin
Box {
    LayerSlot(Layer.Background) { BackgroundPresenter(...) }
    LayerSlot(Layer.Media)      { ... }
    LayerSlot(Layer.Slide)      { SlidePresenter(cue) }      // today's per-type presenters
    LayerSlot(Layer.Captions)   { CaptionsPresenter(...) }
    LayerSlot(Layer.Graphics)   { LowerThirdPresenter(...) }
    LayerSlot(Layer.Announcements) { AnnouncementsPresenter(...) }
    LayerSlot(Layer.Messages)   { MessagePresenter(...) }
}
```

The presenters themselves do not change, except that **they stop drawing their own backgrounds**:
that moves to the Background layer, with each content type's configured background becoming the
default Background cue that goes up with it (so existing setups look the same).

### Backgrounds

The Background layer persists while content changes above it. Going live with content puts that
content's background on the layer, resolved as today (quick tray, then the song or section's own,
then the content type's setting):

- **Default** puts the Default background on the layer.
- **Transparent** clears the layer, so the output is truly transparent behind the content even
  when the Default is an image.
- **Colour, image, video, camera, gradient** put that background on the layer.

If the resolved background is the one already on the layer, nothing happens: no fade, and a video
loop keeps playing. There is no "keep current" option.

Transparency per output stays: today's per-profile background switches become the look including
or excluding the Background layer, so an NDI/OMT alpha, DeckLink key or Browser Source output can
carry text and graphics over transparency while the main screen shows the loop.

The output windows and DeckLink fill/key go through `PresenterModeContent`. The off-screen outputs
(NDI, OMT, Browser Source) dispatch in `OffscreenOutputContent`, and the preview tiles in
`LivePreviewTileParts` — two more copies of the same `when`. Step 2 below folds all three into one
layered renderer, so an output and its preview can never disagree about what is on screen. The
stage monitor (`StageZoneContent`) composes its own zones and is not part of this.

## Commands

One action set, used by the tabs, keyboard shortcuts, the HTTP/WebSocket API, Companion and
Instance Link (and later MIDI/OSC and cue actions):

| Action | Arguments |
|---|---|
| `set` | a cue |
| `cue` | a cue (to preview) |
| `take` | optional layer |
| `clear` | layer |
| `clearAll` | keepBackground |
| `message` | text, optional duration |

Existing endpoints map onto it: `POST /api/project` is `set`, `POST /api/clear` is `clearAll`.
They keep working unchanged; the layer-aware forms are added beside them.

Instance Link mirrors **program** (the layer map) rather than a mode. The follower still renders
locally, on its own outputs with its own looks and settings; no video crosses the link.

**Which layers a follower follows is set up with the link**: one checkbox per layer, all on by
default. A layer left unticked is the follower's own to run, e.g. an overflow room that follows songs
and verses but keeps its own background and nursery messages. Today's `mirrorBackgrounds` becomes
the Background checkbox, so existing links behave as before.

## Migration

In order, each step shippable on its own and each keeping every existing test green:

1. **`LiveShow` beside `PresenterManager`.** `program` is derived from today's state: the current
   `presentingMode` maps to one cue on its layer. Nothing reads it yet. Tests pin the mapping.
2. **Outputs draw from `program`.** One layered renderer replaces the three dispatches
   (`PresenterModeContent`, `OffscreenOutputContent`, `LivePreviewTileParts`);
   with only one layer ever populated, every output looks exactly as today. Screenshot suites must
   not change.
3. **Backgrounds move to their layer.** Presenters stop drawing their own; the per-type background
   becomes the default Background cue. Screenshot suites must not change.
4. **Independent layers.** Lower thirds, captions, announcements and messages stop replacing the Slide layer.
   This is the first visible change, and the first screenshot re-record.
5. **Per-layer Clear and Clear All**, in the UI, shortcuts and API.
6. **Preview / Take** behind the preference.
7. **Looks** replace the `show*` flags, migrating saved profiles.
8. **`presentingMode` retired.** Its readers move to `program` file by file; `Presenting` stays as
   the name of a content type only. What is on air is read off `program` -- `liveContent`,
   `slideContent` and `isLive` -- and the tabs' seams ask `isLive(type)`. The slide's stored
   content type stays inside `PresenterManager` until the content setters write cues themselves.

The benchmark and soak test must show no regression at steps 2, 3 and 4.

## Testing

- Each step's mapping and state transitions are plain unit tests on `LiveShow`.
- Screenshot suites per layer combination (song + lower third, verse + message, background change
  under a song).
- The off-screen benchmark gains a "song + lower third + announcement" scenario at step 4.

## Decisions

1. **Layers**: every layer separate — Background, Media, Slide, Captions, Graphics,
   Announcements, Messages, plus Audio.
2. **Captions** on a layer of their own.
3. **Backgrounds**: per content type, as today, onto a persistent Background layer; Default and
   Transparent as above, no "keep current".
4. **Preview mode** is opt-in; direct Go Live stays the default.
5. **Instance Link**: followers mirror the layer stack, with the followed layers chosen per link.
6. **Step 4 in practice**: lower thirds and captions go up over the slide; announcements, being
   full-screen messages, still replace it (short notices over a song: see decision 7).
   Going live with slide content takes the overlays down. A lower third ending on its own clears the
   whole display by default, as before; a System setting takes down only the lower third instead.
   OBS scene switching, Instance Link and Companion follow the most recent go-live.
7. **Messages** work as today's full-screen notices do: a message going live clears every other
   layer, then goes up alone. It is not an overlay; notices over a song stay the per-display
   "over content" choice for lower thirds and announcements.
