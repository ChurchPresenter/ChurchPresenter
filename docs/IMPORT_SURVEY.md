# Import survey: ProPresenter and EasyWorship

Roadmap step 1.5: what a church moving from ProPresenter or EasyWorship brings, what `:converter`
already carries across, and what it still drops.

## What is already there

`:converter` already reads both products' **songs**:

| Source | Read today (`converter/song/`) |
|---|---|
| ProPresenter 4, 5, 6 (`.pro4`/`.pro5`/`.pro6`, XML) | Title, author, copyright, CCLI number, section groups and their RTF lyrics |
| ProPresenter 7 (`.pro`, protocol buffers) | Name, CCLI block (author, artist, title, publisher, song number), cue groups as sections in group order, RTF lyrics |
| EasyWorship 6/7 (`Songs.db` + `SongWords.db`, SQLite) | `song.title`, `author`, `copyright`, `vendor_id` (CCLI), `word.words` RTF split on blank lines, first line as the section label |
| EasyWorship `.ewsx` / `.ews` schedules | The songs in the schedule; other items stepped over |
| EasyWorship 2007/2009 (`Songs.DB` + `Songs.MB`, Paradox) | As 6/7 |

So "ProPresenter import" in the roadmap is not a new importer. It is closing the gaps below, most of
them outside songs.

## What the formats hold

### ProPresenter 7

A `.pro` file is one `rv.data.Presentation` message (protocol buffers). The community-maintained
schema is [greyshirtguy/ProPresenter7-Proto](https://github.com/greyshirtguy/ProPresenter7-Proto)
(MIT). The schema is incomplete, so unknown fields must be skipped rather than rejected; the
converter's hand-rolled `ProtoMessage` reader already does that.

`Presentation` carries, beyond what is read today:

| Field | What it is | Maps onto |
|---|---|---|
| `arrangements`, `selected_arrangement` | Named section orders ("Sunday", "Acoustic"), each a list of group ids; repeats allowed | **Not read.** Songs come out in group-definition order, so a chorus sung three times appears once. Today's `.song` format has one order; Phase 2 adds arrangements (roadmap 4.4) |
| `background` | The presentation's background (colour or media) | A song background (`SongItem.background`) |
| `cues[].actions[]` | Per-slide actions: media, clear, props, messages, timers | Phase 3 cue actions; stepped over until then |
| `music_key`, `music` | Key and tempo | Tempo onto `SongTuning.bpm`; there is no key field today (the key is implied by the chords) |
| `timeline` | Cues timed against audio | Phase 5 timelines |
| `notes` | Presenter notes | Schedule item notes |

Beyond single `.pro` files:

- **Libraries** are folders of `.pro` files; a folder maps onto a songbook.
- **Playlists** (`.proPlaylist`) are a zip of a protobuf playlist plus the media they use — the
  equivalent of a saved schedule.
- **`.probundle`** is a zip of one `.pro` plus its media.
- **Non-song presentations** (announcements, sermon slides) are the same `Presentation` message
  with media and free-form text elements.

### EasyWorship 6 and 7

Ordinary SQLite under `…\Softouch\EasyWorship\Default\v6.1\Databases\Data\`. Beside the
`Songs.db`/`SongWords.db` pair already read:

| Database | What it holds | Maps onto |
|---|---|---|
| `SongKeys.db` | Song keys | No key field today, as above |
| `SongHistory.db` | When each song was used | Statistics / CCLI reporting history |
| Media and presentation databases | Backgrounds, videos, imported slides | Pictures, media and presentations |
| `.ewsx` `main.db` | A schedule: songs (type 6), plus media, scripture and presentations | A ChurchPresenter schedule |

## Gaps, in the order worth closing

1. **ProPresenter 7 arrangements.** Read `arrangements` and pick `selected_arrangement` (or the
   first), so a song's sung order survives; until songs have arrangements (roadmap 4.4), write the
   selected order out as the section sequence. *Small, in `ProPresenterConverter`, high value: every
   ProPresenter church uses arrangements.*
2. **Libraries as songbooks.** Import a ProPresenter library folder or an EasyWorship database as
   one songbook, keeping the source's name. *Small; mostly UI in `converter/ui/SongsTab`.*
3. **Schedules.** `.proPlaylist` and all of `.ewsx` (not only its songs) into a ChurchPresenter
   schedule, with media copied beside it. *Medium; needs the schedule model from `:core-models`.*
4. **Backgrounds and media.** Copy each song's background and a playlist's media into the media
   library, and set `SongItem.background`. *Medium.*
5. **Tempo, notes, usage history.** Tempo into `SongTuning.bpm`, presenter notes, and
   `SongHistory.db` into statistics. A song key needs a field first. *Small each.*
6. **Non-song presentations.** ProPresenter announcement and sermon presentations as slides.
   *Large: needs the Phase 2 slide model to land in anything better than flattened pictures.*
7. **Cue actions and timelines.** Only once Phase 3 and 5 exist.

Each gap ships with a fixture file from the real product in `converter/src/test/resources`, and an
import report that lists what was dropped, so nothing is lost silently.

## Not in scope

- Writing back to either product.
- ProPresenter's themes and looks: they become relevant with Phase 2 themes, and map onto them
  rather than onto today's output profiles.
