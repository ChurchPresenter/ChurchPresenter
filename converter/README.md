# ChurchPresenter Converter

A cross-platform desktop app for converting church presentation files to [ChurchPresenter](https://github.com/user/ChurchPresenter) formats.

Built with Kotlin Multiplatform and Compose Desktop.

## Features

### Song Converters

- **SNG to SONG** — Convert SongBeamer `.sng` files to `.song` format
  - Supports UTF-8 and Windows-1251 encoded files
  - Preserves verse order metadata
  - Batch conversion (select files or entire folder)

- **SPS to SONG** — Convert SongPresenter `.sps` songbooks to individual `.song` files
  - Text-based `.sps` format (Windows SongPresenter)
  - SQLite `.sps` format (Mac SongPresenter)
  - Extracts songbook name, song metadata (author, composer, tune), and lyrics
  - Automatically structures chorus/verse sections
  - Several song books in one run, or a whole folder of them

- **OpenLP** — the `songs.sqlite` library read directly, or an OpenLyrics `.xml` export
  - Authors resolved through the `authors_songs` bridging table, song numbers through `songs_songbooks`
  - Reads libraries from before either table existed
  - `verse_order` decides the order sections are written in

- **OpenSong** — XML metadata around a plain-text lyrics body, usually with no file extension
  - Chord lines (`.`) and comments (`;`) dropped rather than sung
  - A leading digit inside an unnumbered `[V]` block groups the lines into their own verses
  - `<presentation>` decides the order

- **FreeShow** — `.show` files (JSON)
  - Read in the order the active layout sings them, not the order the slides map lists
  - Child slides continue their parent's section

- **EasySlides** — one `.xml` export is a whole library of `<Item>` songs
  - `[region N]` layout lines dropped
  - Contents with no markers read as blank-line separated verses
  - `<Sequence>` letters mapped properly (`t` is the second chorus, not a tag)

- **Quelea** — `.qsp` song packs and loose song `.xml` files
  - Every zip entry is parsed rather than filtered by name, since Quelea writes `.pdf` onto
    repeated titles
  - Entries that are not songs are reported, not dropped

- **VideoPsalm** — one `.json` file from the `SongBooks` folder is a whole numbered book
  - Read directly rather than repaired into valid JSON: the file has unquoted keys and real line
    breaks inside its strings
  - A chorus stored once per singing becomes one section — the repeats are matched on their lyrics,
    because a book writes the same chorus as `C1` several times and then as `C2`
  - Verses past the ninth, which books stop numbering, keep counting up instead of stacking
  - The `***` / `<><><>` end marker on the last verse is dropped

- **Documents** — lyrics pulled straight out of the files a church already has: `.pdf`, `.docx`,
  `.pptx`, `.ppt` and Keynote `.key`
  - Headings split a file holding several songs into one `.song` each; section labels
    (`Chorus`, `Куплет 2`, …) are recognised, and unlabelled paragraphs become numbered verses
  - Each slide of a deck is its own section, so a song typed one verse per slide imports that way
  - Keynote is read through the app's own presentation engine — both the modern format and, via
    the preview Keynote embeds, documents it cannot open natively

### Bible Converter

- **XML to SPB** — Convert Zefania XML bible files to `.spb` format
  - Supports 60+ languages with localized book names
  - Handles right-to-left languages (Arabic, Hebrew, Syriac)
  - Batch conversion with recursive folder scanning

### Duplicate Song Finder

- **Line-level fingerprinting** — detects duplicates even with missing verses or spelling errors
- **Inverted index** for fast scanning of large collections (1000+ songs)
- **Latin/Cyrillic homoglyph detection** — finds and fixes mixed-script characters that prevent matching
- **Side-by-side compare** — GitHub-style diff view with file selection for groups with multiple files
- **Manual file selection** — checkbox each file for deletion, or auto-select same-folder duplicates
- Matches by song number, title (content + filename), and lyrics similarity
- Open files directly from the compare dialog

### Bulk Rename

- Rename `.song` files across subdirectories recursively
- Strip leading numbers (e.g. `0111 - Title.song` → `Title.song`)
- Rename to first verse line
- Case conversion: Sentence case, Title Case, lowercase, UPPERCASE
- Live preview updates when changing options
- Compare conflicting files side-by-side with diff view
- Mark and delete duplicates from the rename tab

### General

- Preview before converting — see exactly what will happen before committing
- Overwrite warnings for existing output files
- File pickers default to Downloads folder
- Dark theme UI

## Download

Download the latest release from the [Releases](../../releases) page.

## Build from Source

The converter is the `:converter` module of the [ChurchPresenter](https://github.com/ChurchPresenter/ChurchPresenter)
build, so every command below is run from the repository root with that repo's wrapper.

### Requirements

- JDK 21+

### Run

```bash
./gradlew :converter:run     # the converter on its own; the app opens it from its Help menu
./gradlew :converter:test
```

### Package

```bash
# Windows installer
./gradlew :converter:packageMsi

# macOS
./gradlew :converter:packageDmg

# Linux
./gradlew :converter:packageDeb
```

## File Formats

### .sng (SongBeamer)

Text file with `#Key=Value` headers and `---`-separated verse sections.

### .sps (SongPresenter)

Either a text file with `#$#`-delimited song entries or a SQLite database. Contains an entire songbook of songs.

### .song (ChurchPresenter)


### .xml (Zefania XML Bible)

Standard Zefania XML Bible Markup with `BIBLEBOOK > CHAPTER > VERS` structure.

### .spb (ChurchPresenter Bible)

Tab-separated text file with header metadata and `B001C001V001` verse identifiers.

## License

MIT
