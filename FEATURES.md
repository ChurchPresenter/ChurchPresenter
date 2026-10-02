# Church Presenter — Features

**Everything your church needs to put words on the screen — songs, scripture, slides, video, captions and broadcast graphics — in one free, open-source app.** 

> Source paths below are relative to `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/`
>
> Except `data/settings/…` and `data/SettingsManager.kt`, which are shorthand for the `:settings`
> module: every one of those files sits flat in `settings/src/main/kotlin/org/churchpresenter/settings/`
> (package `org.churchpresenter.settings`). Only `data/settings/ObsSceneSelection.kt` is really under
> the app's `data/settings/`.

## Songs & Lyrics
- **Unlimited song library** — organize thousands of songs across as many songbooks as you like, indexed straight from a folder.
- **Powerful search** — find songs by title or number with contains, starts-with, exact-match and phrase filters, plus category and songbook filters. A text search looks through every language's titles and lyrics, and each hit says where it matched — the title, the section by its own name (Verse 1, Chorus) or the lyrics, and which language — with the match highlighted. Digits alone search song numbers.
- **Built-in song editor** — add and edit songs with simple verse/chorus formatting; no external tools needed.
- **Chords made easy** — transpose a whole song up or down a semitone at a time (every language moves together, and Save keeps the new key), pick chords from the key you are writing in, or build any chord from a root and a type — major, minor, 7, maj7, m7, 6, m6, 9, add9, sus2, sus4, dim, dim7, m7♭5 and aug.
- **Song Library Manager** — every song in one editable grid: search, filter by song book, show the columns you care about, type straight into a cell, and change a field on a whole selection at once. Renumbering a book, filling in missing composers or moving a set into a new song book is one screen rather than one song at a time. **Compare translations** puts a song's languages side by side, section by section, and flags sections out of step and languages missing a title or lyrics.
- **Bilingual worship** — show two languages at once, side-by-side or stacked, or switch between primary and secondary on the fly.
- **Look-ahead for the band** — see the current and next section in advance so transitions stay smooth.
- **Favorites & play counts** — star the songs you use most and see how often each has been sung.
- **Bring your existing library** — the bundled converter reads SongBeamer, OpenLP (the `songs.sqlite` database itself or an OpenLyrics export), OpenSong, FreeShow, Free Worship, EasySlides, Quelea, VideoPsalm and SoftProjector libraries, plus lyrics pulled out of PDF, Word, PowerPoint and Keynote files — several files or a whole folder at a time — and ready-to-use sample songs ship with the app.
- **Flexible display** — one verse or one line at a time, optional title slides, and full control over how numbers and titles appear.
- **A background per song** — give a song, or one of its sections, its own colour, gradient, picture, video or camera, separately for full screen and lower third, with a look (dim, blur, opacity) on top — or let it follow the profile's background.

**Source locations:**
- `tabs/SongsTab.kt` — main UI
- `viewmodel/SongsViewModel.kt`, `viewmodel/SongSettingsViewModel.kt`, `viewmodel/SongFolderWatcher.kt`
- `data/Songs.kt`, `data/SpsConverter.kt`; `SongItem` and `SongFileParser` are in `:core-models` (`models/songs/`)
- `viewmodel/SongSearchMatch.kt`, `tabs/SongListPane.kt` (with `SongListScope.kt`, `SongTableHeader.kt`, `SongListRows.kt`) — where each search hit matched, and the list that draws it
- `dialogs/SongBackground*.kt` — the per-song background panel
- `data/settings/SongSettings.kt`
- `presenter/SongPresenter.kt`, with `SongLook.kt`, `SongFrame.kt`, `SongFitFrame.kt` and `SongSlide*.kt` beside it
- `dialogs/EditSongDialog.kt`, `dialogs/tabs/SongSettingsTab.kt`
- `composables/SongChordPreview.kt` — the editor's chord preview: Transpose, the key's palette and the chord picker
- `core-models/src/main/kotlin/.../models/songs/LyricSection.kt` (the `:core-models` module)
- `converter/` (the `:converter` Gradle module, at the repo root) — format converter tool
- `songlibrary/` (the `:songlibrary` Gradle module) — the Song Library Manager grid; `TranslationComparison.kt` and `ui/CompareTranslations*.kt` — Compare translations
- `song-chords/` (the `:song-chords` Gradle module) — chord parsing, transposition and chord-sheet import
- `core-models/` (the `:core-models` Gradle module) — `models.songs`: the song, the `.song` file format and the library folder, shared by the app and the library window

## Bible & Scripture
- **Instant verse display** — browse any of the 66 books and put a verse on screen in seconds.
- **Dual and multi-translation modes** — keep the original primary/secondary workflow (including
  lower-third styling), or switch to an independent ordered stack of any number of translations.
  Multi-translation mode uses one full-screen typography profile per translation and has no
  lower-third-specific settings.
- **Multi-verse ranges** — select and present several verses at once with Ctrl/Shift click.
- **Search the whole Bible** — search across the entire text or just the current book.
- **History** — jump back to recently shown passages instantly.
- **Cross references** — see the passages that cross-reference the verse you are on, and jump to one in a click.
- **Strong's dictionary** — explore original Hebrew and Greek words with transliteration, pronunciation, definitions and KJV usage.
- **Download translations in-app** — browse over 1,500 translations in more than 1,000 languages from eBible.org and the Zefania XML archive, filter by language, and install one in a click: it downloads and converts on your machine, straight into your Bible folder, with its copyright shown up front.
- **Follow along automatically** — connect a live speech-to-text feed and the app listens for spoken Bible references — stated outright or simply the next verse in a passage being read — and stages or goes live with the matching verse on its own, tiered by confidence so only clear matches jump straight to the screen.

**Source locations:**
- `tabs/BibleTab.kt` — main UI; its pieces in `tabs/BibleTab*.kt`
- `tabs/DictionaryTab.kt` — Strong's dictionary UI
- `dialogs/tabs/ProfileDictionaryPage.kt`, `dialogs/tabs/DictionaryPart.kt` — how the dictionary card looks on each output, edited on Profiles → Dictionary
- `viewmodel/BibleViewModel.kt`, `viewmodel/DictionaryViewModel.kt`
- `tabs/BibleCrossReferences.kt`, `tabs/BibleCrossReferenceState.kt`, `tabs/BibleHistoryPanel.kt`, `tabs/BibleDetectionPanel.kt`, `tabs/BibleTranslationOrder.kt`
- `viewmodel/BibleEngineClient.kt` — auto-follow speech detection client
- `bible/` (the `:bible` Gradle module) — `Bible.kt`, `BibleBook.kt`, `BibleSearch.kt`,
  `BibleVerse.kt`, `BibleTranslationNames.kt` and the `.spb` format helpers in `SpbFormat.kt`
- `data/BibleBookNames.kt`, `data/BibleBookAbbreviations.kt` — these stay in the app: they resolve
  Compose string resources, which `:bible` deliberately has no access to
- `data/StrongsEntry.kt`
- `bible-formats/` (the `:bible-formats` Gradle module) — the download catalogues and the `.spb` converters behind them
- `bible-formats/src/main/kotlin/.../catalog/` — `EBibleSource` (eBible.org, USFX), `ZefaniaSource` + `ZefaniaRepositoryIndex`, `BebliaSource` + `BebliaCatalogIndex`
- `viewmodel/BibleCatalogViewModel.kt`, `dialogs/BibleCatalogBrowserDialog.kt` — download browser UI
- `bible-formats/src/main/kotlin/.../UsfxToSpbConverter.kt`, `XmlToSpbConverter.kt` — the conversions
- `data/settings/BibleSettings.kt`, `data/settings/BibleEngineSettings.kt`
- `presenter/BiblePresenter.kt`, with `BibleLook.kt`, `BibleFrame.kt`, `BibleSlide.kt`, `BibleLayouts.kt` and `PresenterBackdrop.kt` beside it
- `dialogs/tabs/BibleSettingsTab.kt`
- `core-models/src/main/kotlin/.../models/bible/SelectedVerse.kt` (the `:core-models` module)
- `bible-engine/` (the `:bible-engine` Gradle module, at the repo root) — Bible Lookup Engine (speech-to-reference detection)
- `shared-ui/…/utils/TrainingDataLogger.kt`, `LiveHistoryLogger.kt` (the `:shared-ui` module) — the session logs in
  `~/.churchpresenter/bible-stt-logs/`, named after the STT session id (read from STT's
  `/api/health` on connect and from its socket payloads) or else the app's start time, opened by one `{"type":"session"}` header
  line and deleted after 30 days. `live-content-<session>.jsonl` is the on-screen history, one JSON
  line per change of what is on screen, repeats dropped: `ts_ms`, `sessionId`, `contentType` (a
  `Presenting` name, `NONE` when cleared), `source` when known, plus identifiers only — never text.
  For lyrics: `songId`, `songbook`, `songNumber`, `songTitle`, `sectionIndex`, `sectionType`,
  `lineIndex`. For Bible: `verseCode` (canonical `BxxxCxxxVxxx`) and `reference`. For presentations:
  `fileName` and `slideIndex`. For media and pictures: `fileName`.

## Slides & Presentations
- **PowerPoint, Keynote & PDF** — drop in `.pptx`, `.ppt`, `.key` or `.pdf` files and present them as slides — no Microsoft or Apple software required.
- **Real animations, not just static slides** — PowerPoint and Keynote entrance, emphasis and exit effects, per-paragraph text builds, click-step sequencing, motion paths and slide transitions all play back live, right in the app.
- **Slide thumbnails & navigation** — see every slide at a glance and jump anywhere.
- **Presenter notes** — speaker notes from PowerPoint and Keynote flow straight to your stage monitor.

**Source locations:**
- `slides/…/tabs/PresentationTab.kt` (the `:slides` module) — main UI; its pieces in `PresentationTabScope.kt`, `PresentationTopBar.kt`, `PresentationControlsBar.kt`, `PresentationBody.kt`
- `slides/…/viewmodel/PresentationViewModel.kt`
- `presenter/PresentationPlayer.kt` (the app), `slides/…/presenter/PresentationPresenter.kt` and `PresentationFrame.kt` — animated playback
- `presentation-engine/` (the `:presentation-engine` Gradle module, at the repo root) — PPTX/Keynote parsing, timing and animation engine
- `data/settings/PresentationSettings.kt`
- `server/CompanionServer.kt` — slide API for mobile (background rendering)

## Images & Media
- **Image slideshows** — point to a folder and present photos with crossfade, fade and slide transitions, auto-advance and looping.
- **Hide what you won't show** — hide any picture or presentation slide from its tile, as in PowerPoint: Next, Previous and the slideshow pass over it, you can still click it to show it on purpose, and it stays hidden the next time the folder or file is opened.
- **Audio & video playback** — play local files or network streams (HTTP, RTSP and more), powered by VLC.
- **Full transport controls** — play, pause, seek, volume, mute, and choose your audio output device.
- **Background audio** — music keeps playing while you switch tabs or show other content.
- **Fit, fill or stretch per screen** — each output profile scales pictures and video its own way, so the wide wall fills while the portrait confidence screen fits; the tabs' scale buttons set every profile at once.
- **Subtitles styled per screen** — SRT/WebVTT subtitles take their colour, font, size and position from each output's profile.

**Source locations:**
- `slides/…/tabs/PicturesTab.kt` (the `:slides` module) — image slideshow UI; its pieces in `PicturesTabScope.kt`, `PicturesHeader.kt`, `PicturesControlsBar.kt`, `PicturesGrid.kt`
- `media/…/tabs/MediaTab.kt` (the `:media` module) — audio/video UI; its pieces in `MediaTabScope.kt`, `MediaSourceBar.kt`, `MediaControlsBar.kt`
- `slides/…/viewmodel/PicturesViewModel.kt`, `media/…/viewmodel/MediaViewModel.kt`, `media/…/viewmodel/LocalMediaViewModel.kt`
- `slides/…/data/HiddenItemsStore.kt`, `shared-ui/…/composables/SlideshowHideToggle.kt` — hidden pictures and slides, remembered per folder and file, and the eye that hides them
- `data/settings/PictureSettings.kt`
- `slides/…/presenter/PicturePresenter.kt`, `media/…/presenter/MediaPresenter.kt`
- `media/…/composables/VideoPlayer.kt`
- `media/…/dialogs/tabs/MediaSettingsTab.kt`
- `dialogs/tabs/ProfileScaleRow.kt`, `shared-ui/…/utils/OutputScaleMode.kt` — per-profile scaling and the tabs' shortcut over it
- `dialogs/tabs/ProfileOverlayPages.kt`, `dialogs/tabs/DisplayTextRows.kt` — the subtitle look, edited on Profiles → Subtitles

## Lower Thirds & Graphics
- **Animated lower thirds** — display polished Lottie animations for names, titles and welcomes.
- **Drop in a folder, ready to send** — point the app at a folder of Lottie files and every one of them is listed, ready to go live, trigger from Companion or upload to an ATEM media pool.
- **Built-in generator** — design your own animated lower thirds with the included Lottie generator — no After Effects needed.
- **Fine timing control** — pause on a frame, hold, and play through with smooth fade in/out.
- **Every script drawn right** — Tamil, Hindi, Arabic, Thai and other scripts whose letters join or change shape are drawn as whole lines, so vowel signs stay on their letters. The generator's Text shaping setting (Auto, Whole lines, Letter by letter) is saved in the file, and Auto picks whole lines only for text that needs them.
- **Animated Bible lower third** — opt in to a Lottie band for scripture: the band slides, wipes, unrolls or fades in on Go Live, each verse types, scrolls like a ticker, fades or slides in as you step through a passage, and everything animates out on Escape. Design it in the built-in Bible band generator, with your Bible fonts, sizes and colors filled in live, for one or two languages.

**Source locations:**
- `tabs/LowerThird.kt` — main UI; its pieces in `tabs/LowerThird*.kt`
- `presenter/LowerThirdPresenter.kt`, `presenter/LowerThirdOffscreenRenderer.kt`
- `lottieGenerator/.../lottie/TextShaping.kt` — the Text shaping setting every Lottie player reads from the file
- `server/LowerThirdSequencer.kt`
- `presenter/BibleLottieBand.kt`, `presenter/BibleLottieTemplate.kt`, `presenter/BibleLottieTextFit.kt`, `presenter/BibleBandClock.kt` — the Bible band at run time; driven from `PresenterTransitionEffects.kt`
- `dialogs/tabs/BibleLottieBandPicker.kt` — the template picker, the Bible tab's Lower Third Animation section and the generator window
- `lottieGenerator/src/main/kotlin/.../band/` (the `:lottieGenerator` module) — the Bible band generator

## Announcements & Timers
- **On-screen announcements** — show text anywhere on screen with a wide range of slide and scroll animations, custom colors, speed and looping. The tab is laid out around a full-height preview in the output's own shape, with the text and the timer side by side and their position, background and animation under the preview.
- **Countdown timers** — count down to a duration or to a specific clock time, with custom colors and an end-of-countdown message — perfect for "service starts in…".

**Source locations:**
- `tabs/AnnouncementsTab.kt` — main UI; its pieces in `tabs/Announcements*.kt`
- `viewmodel/AnnouncementsViewModel.kt`
- `data/settings/AnnouncementsSettings.kt`
- `presenter/AnnouncementsPresenter.kt`
- `utils/TimerStateManager.kt`

## Web & Canvas
- **Live websites on screen** — present any web page with bookmarks, navigation and zoom, and even type into live pages.
- **Canvas scene compositor** — build layered scenes from images, text, video, shapes, gradients, clocks, QR codes, live cameras, screen capture, NDI and OMT sources from the network, web pages and Bible verses — like a mini production switcher inside the app.
- **One scene for landscape and portrait screens** — give a scene a second layout from its size menu and arrange both side by side: the same layers, each placed on its own per layout. Every output draws the layout that matches its shape.
- **QR codes made easy** — generate QR codes for URLs, WiFi, contact cards, email, SMS and more, right on the slide.
- **Cameras that just work** — ChurchPresenter carries its own copy of ffmpeg, so a webcam or capture card can be put on the canvas without installing anything first. Point it at a different ffmpeg from Settings → Projection if you would rather use your own.
- **OMT sources on the canvas** — receive any Open Media Transport source as a layer, picked from what is on the network or reached directly by its `omt://` address, with a preview-stream option for a small layer. Nothing to install.
- **NDI sources on the canvas** — receive any NDI source on your network as a layer: a camera from another machine, a graphics feed, an overflow room's output. Pick it from a list of what is sending, or drop to the sender's low-bandwidth proxy for a small layer on a busy network. Needs the same free NDI Runtime as NDI output.

**Source locations:**
- `tabs/WebTab.kt` — web browser UI; its pieces in `tabs/WebTabScope.kt`, `WebToolbar.kt`, `WebPreview.kt`
- `tabs/CanvasTab.kt` — scene compositor UI; its pieces in `tabs/CanvasTabScope.kt`, `CanvasLeftPanel.kt`, `CanvasAddSourceMenu.kt`, `CanvasCenterPanel.kt`
- `viewmodel/SceneViewModel.kt`
- `core-models/src/main/kotlin/.../models/scene/SceneModels.kt` (the `:core-models` module) — including a scene's second layout and which one an output draws
- `composables/SceneCanvas.kt`, `composables/SceneSourceRenderer.kt`, `composables/SourcePropertiesPanel.kt`
- `tabs/CanvasSizeMenu.kt`, `tabs/CanvasPlacement.kt` — a scene's size and layouts, and layers left off the canvas
- `composables/SharedBrowserFrameCache.kt`, `composables/SharedCameraFrameCache.kt`
- `composables/NdiFrameCache.kt`, `composables/NdiSourceDirectory.kt` — receiving NDI sources onto the canvas, and finding them
- `composables/OmtFrameCache.kt`, `composables/SceneOmtEditor.kt`, `composables/ReceivedFrameCache.kt` — receiving OMT sources onto the canvas, choosing one, and the capture loop both protocols share
- `shared-ui/…/utils/FfmpegBinary.kt`, `dialogs/tabs/ProjectionFfmpegCard.kt` — which ffmpeg cameras are opened with: the bundled one, an override, or whatever is installed
- `gradle/ffmpeg-builds.properties`, `THIRD_PARTY_FFMPEG.md` — where the bundled ffmpeg comes from, and its licence
- `presenter/ScenePresenter.kt`, `presenter/WebsitePresenter.kt`
- `data/settings/WebBookmark.kt`

## Live Captions & Translation
- **Real-time captions** — connect a speech-to-text server to caption your service live.
- **Live translation** — show transcription, translation, or both together in stacked or side-by-side layouts.
- **Styled per screen** — each output profile sets its own caption look, how many lines and segments it keeps, and whether words type out as they arrive.
- **Easy to read** — fade captions out after a silence, cap how fast words arrive so every line can be read, slide lines up instead of jumping, dim older lines, and start a new line for each phrase or sentence, with a maximum line length.
- **Three presentations** — a rolling transcript, pop-on blocks that fill and clear so nothing moves while it is read, or a one-line ticker; in a rounded card or a full-width band, flush with the screen edge or held off it.
- **Two languages your way** — stacked, side by side, or interleaved with each line followed by its translation; either language first; sharing a box or each in its own; the translation in its own size, capitals, weight and slant.

**Source locations:**
- `tabs/STTTab.kt` — main UI
- `viewmodel/STTManager.kt`
- `data/settings/STTSettings.kt`
- `presenter/STTPresenter.kt`
- `dialogs/tabs/ProfileCaptionsPage.kt`, `dialogs/tabs/CaptionReadingGroup.kt` — the caption look and reading settings, edited on Profiles → Live captions
- `presenter/CaptionBody.kt` (line breaks, wrapping, capitals, dimming), `presenter/CaptionLook.kt` (card, band, margins, silence fade), `presenter/CaptionTicker.kt`, `presenter/CaptionInterleave.kt`, `presenter/CaptionLanguages.kt`
- `composables/CaptionText.kt` — `BottomAlignedText`, which draws only the lines in its window (also used by video subtitles)
- `dialogs/STTSettingsDialog.kt` — the install-wide Bible-engine options

## Audience Q&A
- **Questions from the congregation** — people scan a QR code and submit questions from their phones.
- **Full moderation** — approve, deny, sort and queue questions before any go live.
- **Anywhere access** — optional public access lets people ask over mobile data without joining your WiFi.
- **Voting & history** — let the room upvote approved questions, and export the session afterward.
- **Styled per screen** — each output profile sets how a question and its QR code look on it.

**Source locations:**
- `tabs/QATab.kt` — main UI
- `viewmodel/QAManager.kt`
- `data/settings/QASettings.kt`
- `presenter/QAPresenter.kt`
- `dialogs/tabs/ProfileOverlayPages.kt` — the question and QR look, edited on Profiles → Q&A
- `dialogs/QARemoteDialog.kt` — links, public access, rate limit and the QR message
- `core-models/src/main/kotlin/.../models/qa/Question.kt` (the `:core-models` module)

## Service Planning
- **Drag-and-drop schedules** — build your whole service from songs, scripture, slides, media, lower thirds, announcements and websites.
- **Save & reopen services** — store schedules as files and pick up exactly where you left off, with autosave and crash recovery.
- **Stay organized** — color-coded labels, per-item notes, quick reordering, recents and full undo/redo; Add Item stays pinned below the run of show however long it grows.

**Source locations:**
- `tabs/ScheduleTab.kt` — main UI
- `viewmodel/ScheduleViewModel.kt`
- `core-models/src/main/kotlin/.../models/schedule/ScheduleItem.kt` (the `:core-models` module)
- `viewmodel/FileManager.kt`
- `dialogs/AddLabelDialog.kt`

## Calendar & Planning
- **Calendar Manager** — every planned service on a month grid, each with its own run of show, opened from the Help menu. Build a service from songs, scripture, media and cues, time each row, save services as templates, and load one into the Schedule when it is time.
- **Automated runs** — a row can start on its own, run for a set time and repeat; cues blank the outputs or go live at a time relative to the service start.
- **Services on the band's phones** — pair a phone with a QR code and it sees the planned services, their runs of show and the songbooks, synced end-to-end encrypted through a relay.
- **Print it** — export a service's run of show as a PDF.
- **Planning Center import** — bring a Planning Center Services plan's songs, media and order of service straight into the Schedule, with its songs matched against your library. Each church uses its own free PCO developer credentials; nothing is written back.

**Source locations:**
- `calendar/` (the `:calendar` Gradle module) — the Calendar Manager window, its model and the PDF export
- `dialogs/CalendarEnrollQrDialog.kt`, `dialogs/tabs/CalendarSyncCard.kt`, `server/CalendarRelayAccess.kt` — pairing a phone and syncing through the relay
- `planning-center/` (the `:planning-center` Gradle module) — the Planning Center client
- `dialogs/PlanningCenterImportDialog.kt`, `viewmodel/PlanningCenterImportViewModel.kt` — the import window

## Projection & Output
- **Unlimited outputs** — drive as many screens as you have — one window per connected display, plus every DeckLink/SDI device. No artificial limit.
- **Output profiles** — each output follows a named, reusable profile that says what it shows and how it looks: its content, its Bible translations in its own order, and its Bible, song, background, caption, subtitle, Q&A and dictionary styling, previewed live with its real background at any screen shape. Two screens that share a profile stay identical; change one profile to restyle them both.
- **Linked profiles** — a profile can follow another and keep only what it changes: the overflow room is the sanctuary with a smaller font. Every value shows where it comes from, and one click reverts it, unlinks the profile or links it back.
- **Style one translation or language** — set a look for all of them, then give one translation or song language a size, colour or position of its own.
- **Adjust on the preview** — drag margins, position, width, text size and the band height straight on the picture, or open it across the window for finer steps. Click any song element, Bible translation or reference to point the settings at it and drag it on its own; Reset positions puts everything back.
- **Text boxes** — give any piece of text on a profile page a box of its own: a song's title, number, label or one language of its lyrics, a Bible translation's verse or reference, a caption, a subtitle, a Q&A question or QR message, a dictionary word, a stage zone. Drag and resize it on the preview; the text shrinks to fit it, is cut off or spills over, sits at its top, middle or bottom, and can fill the box. Boxes can keep clear of each other and snap to guides, measured against the whole screen or inside the margins.
- **Room to lay out** — margins go up to nine tenths of the screen, the gap between song languages is set per profile, each language can be fitted on its own, and the content region can move just the text while the background keeps the whole screen.
- **See what you changed** — every setting a profile holds at other than its default is listed beside the preview, with the default and a Revert for each.
- **Full screen or lower third** — present full-screen or as a lower-third band, per content type.
- **Beautiful backgrounds** — solid colors, images, looping video, gradients or transparent — set defaults and per-type overrides.
- **Built-in stock photo & video search** — search and download from Pexels and Pixabay right inside the app with a free API key, plus a set of preloaded backgrounds ready to use offline.
- **Broadcast fill + key** — output separate fill and key signals for hardware keying, including SDI via Blackmagic DeckLink.
- **Browser Source streaming output** — a transparent, OBS-ready browser-source overlay with true alpha transparency, crossfaded mode switching and configurable per-output resolution/fps — for lower thirds, media, websites and more, no OBS scene-switching integration required.
- **OMT output** — send live content as an Open Media Transport source, the open-source alternative to NDI that vMix, OBS (with the OMT plugin) and FreeShow receive. Alpha mode carries real transparency in the one source, so a lower third arrives already keyed; choose the encoding quality, or let each receiver ask for what it needs. Nothing to install — the OMT libraries ship with the app.
- **NDI output** — send live content over the network as an NDI® source and pick it up in OBS, vMix or a hardware switcher, with no capture card. Alpha mode carries genuine per-pixel transparency, so a lower third arrives already keyed — no second source, no downstream keyer. Fill-only and discrete fill + key are there for gear that wants them. Needs the free NDI Runtime, installed separately and detected automatically, exactly as VLC is.
- **Typography that fits** — auto-fit text to the screen, with control over fonts, size, alignment, shadows and margins.
- **Live preview** — always see exactly what's on screen, and lock any output to a chosen tab.
- **Design the preview panel** — arrange the live previews the way the booth wants them: start a layout from a template, split areas across or down, drag the dividers, choose what each area shows and where it sits, keep several named layouts and switch between them, and let a layout fill the panel.

**Source locations:**
- `PresenterScreen.kt` — output window
- `shared-ui/…/models/Presenting.kt` — active-content state enum
- `presenter/DeckLinkComposeOutput.kt`
- `presenter/BrowserSourceVideoRenderer.kt`, `presenter/LocalTransparentBlanking.kt` — Browser Source output
- `presenter/ComposeScenePump.kt`, `presenter/OffscreenOutputContent.kt` — the off-screen render both virtual outputs share
- `ndi/` (the `:ndi` Gradle module) — NDI itself: `NdiRuntime`, `NdiLibrary`/`JnaNdiLibrary`, `NdiSender` and `NdiOutputMode`
- `presenter/NdiVideoRenderer.kt`, `presenter/NdiManager.kt`, `dialogs/tabs/ProjectionNdiCard.kt` — the app-side wiring and its settings card
- `omt/` (the `:omt` Gradle module) — OMT itself: `OmtRuntime`, `OmtLibrary`/`JnaOmtLibrary`, `OmtSender`, `OmtReceiver`, `OmtDiscovery`
- `presenter/OmtVideoRenderer.kt`, `presenter/OmtOutputRegistry.kt`, `presenter/OmtManager.kt`, `dialogs/tabs/ProjectionOmtCard.kt` — the app-side OMT wiring and its settings card
- `gradle/omt-builds.properties`, `.github/workflows/omt-linux.yml`, `THIRD_PARTY_OMT.md` — where the bundled OMT libraries come from, and their licence
- `media/…/data/StockMediaClient.kt`, `media/…/dialogs/StockMediaBrowserDialog.kt`, `media/…/viewmodel/StockMediaViewModel.kt`, `data/settings/StockPhotoSettings.kt`
- `composables/DeckLinkManager.kt`, `composables/DeckLinkInputGate.kt`, `composables/LivePreviewPanel.kt`, `composables/LoopingVideoBackground.kt`
- `viewmodel/PresenterManager.kt`, `viewmodel/BackgroundSettingsViewModel.kt`
- `data/settings/BackgroundConfig.kt`, `data/settings/BackgroundSettings.kt`, `data/settings/ProjectionSettings.kt`, `data/settings/ScreenAssignment.kt`
- `dialogs/tabs/BackgroundSettingsTab.kt`, `dialogs/tabs/ProjectionSettingsTab.kt`
- `shared-ui/…/utils/AutoFitUtils.kt`
- `dialogs/tabs/ProfilesSettingsTab.kt`, `dialogs/tabs/ProfileEditor.kt`, `dialogs/tabs/ProfileHeader.kt` — the Profiles tab: the list, the editor, the header
- `dialogs/tabs/ProfileContentPage.kt`, `dialogs/tabs/ProfileSourcePickers.kt`, `dialogs/tabs/ProfileSources.kt` — what a profile shows, and its Bible and song sources
- `dialogs/tabs/PreviewShape.kt`, `dialogs/tabs/PreviewShapeChooser.kt` — the preview's shape: presets, a custom ratio or a custom resolution
- `dialogs/tabs/ProfileList.kt`, `dialogs/tabs/ProfileListDrag.kt`, `data/settings/OutputProfileOrder.kt` — the profile list and its order: drag, Alt+↑/↓, the right-click menu
- `dialogs/tabs/ProfileSectionNav.kt`, `dialogs/tabs/ProfilePage.kt`, `dialogs/tabs/SettingsGroup.kt`, `dialogs/tabs/SettingsRowControls.kt` — the section list, and the cards and rows every page is built from, with Basic / Advanced
- `dialogs/tabs/ProfileGeneralPage.kt`, `dialogs/tabs/ProfileOutputsPage.kt`, `dialogs/tabs/ProfileBiblePage.kt`, `dialogs/tabs/ProfileSongsPage.kt`, `dialogs/tabs/ProfileBackgroundPage.kt`, `dialogs/tabs/ProfileStagePage.kt` — the pages
- `data/settings/LinkedProfiles.kt`, `data/settings/LinkedProfilePaths.kt`, `data/settings/LinkedProfileValues.kt`, `dialogs/tabs/ProfileLink*.kt` — linked profiles: a master, and profiles that keep only what they change
- `data/settings/BibleAllLayer.kt`, `dialogs/tabs/SongAllLanguages.kt`, `dialogs/tabs/ProfileStyleTarget.kt` — "Applies to": All, or one translation or language with values of its own
- `dialogs/tabs/PreviewAdjust*.kt`, `dialogs/tabs/LargePreview.kt`, `presenter/PresentedBlock.kt` — adjusting a page from its preview, and the preview across the window
- `dialogs/tabs/SongElementMove.kt`, `presenter/SongElementMove.kt`, `presenter/BibleBlockShift.kt` — moving one song element, one Bible translation or its reference on its own, and Reset positions
- `data/settings/ProfileDefaults.kt`, `dialogs/tabs/ProfileLinkCard.kt` — what a profile changes from the defaults, listed beside the preview with Revert
- `dialogs/tabs/CustomizePane.kt`, `dialogs/tabs/ProfileFormStages.kt`, `dialogs/tabs/PreviewBackgroundLayer.kt`, `dialogs/tabs/Customize*.kt` — the picture beside each page, with the output's real background
- `data/settings/OutputProfile.kt`, `data/settings/OutputProfileResolution.kt` — the profile, and what an output renders with
- `data/settings/TextBox.kt` — text boxes: the box, its options and the keys items are boxed under
- `presenter/TextBoxLayout.kt`, `presenter/SongBoxLayer.kt`, `presenter/BibleBoxLayer.kt`, `presenter/SongSlideFit.kt` — drawing boxed items, and fitting what is left and each language on its own
- `dialogs/tabs/TextBoxRows.kt`, `dialogs/tabs/BoxItem.kt`, `dialogs/tabs/ItemBoxGroup.kt`, `dialogs/tabs/SongBoxRows.kt`, `dialogs/tabs/BibleBoxTarget.kt`, `dialogs/tabs/PreviewAdjustBoxes.kt` — a page's box rows, and moving and resizing boxes on the preview
- `dialogs/tabs/MarginRoom.kt`, `presenter/ContentRegionModifier.kt`, `dialogs/tabs/ContentBackgroundOwn.kt` — how far margins go, a region that moves only the text, and a content background that remembers its own
- `data/settings/PreviewLayouts.kt`, `data/settings/PreviewLayoutSettings.kt` — preview layouts: the area tree and the layouts kept on the projection settings
- `composables/PreviewLayoutView.kt`, `composables/PreviewLayoutTemplate.kt`, `composables/PreviewGroupsPopover.kt` — the panel drawn and edited as its layout says, the templates, and the gear's layout list

## Stage Monitor
- **Confidence display for the platform** — give worship leaders and speakers their own screen showing the current slide, next slide, a clock, the countdown timer, section labels and presenter notes — in vertical, horizontal or four-quadrant layouts.
- **Transpose on the band's tablets** — open a Browser Source stage view on a tablet and move its chords up or down a semitone with −1 / 0 / +1 buttons: for a capo, or a singer who needs another key. Only that output changes — the song, the main screen and the OBS feed stay as written. Turned on per profile, and each tablet is approved on the desktop once.

**Source locations:**
- `StageMonitorScreen.kt`
- `data/settings/StageMonitorSettings.kt`
- `server/BrowserSourcePage.kt`, `server/BrowserSourceRoutes.kt` — the tablets' transpose buttons, and the routes that approve and apply them; `LiveStatusWiring.kt` (`offersTranspose`) says which outputs offer them
- `dialogs/tabs/ProfileStagePage.kt`, `dialogs/tabs/ProfileStageText.kt`, `dialogs/tabs/StageMonitorZoneGrid.kt` — the Stage layout page of a stage-monitor profile

## Mobile & Remote Control
- **Control from your phone** — a built-in server lets phones and tablets browse songs and scripture, build the schedule and go live — all over your local network.
- **You stay in charge** — remote actions ask for approval on the desktop, with per-device allow/block lists and optional API-key protection.
- **Real-time sync** — connected devices update instantly as the schedule and content change.

**Source locations:**
- `server/CompanionServer.kt` — Ktor REST + WebSocket server
- `server/SslCertificateManager.kt`, `server/TunnelManager.kt`
- `data/RemoteClientManager.kt`
- `data/settings/ServerSettings.kt`
- `dialogs/tabs/ServerSettingsTab.kt`
- `dialogs/RemoteActivityToast.kt`, `dialogs/RemoteEventDialog.kt`

## Multi-Room & Instance Linking
- **Follow another instance live** — link a second ChurchPresenter instance — an overflow room, a secondary campus, a confidence feed — so it automatically mirrors whatever the primary sends live: Bible, songs, pictures, presentations, media, canvas, Q&A and dictionary entries.
- **Resilient by design** — automatic reconnect with backoff, a heartbeat that surfaces a dead link within seconds instead of freezing on stale content, and command acknowledgement so remote actions never silently fail.

**Source locations:**
- `server/InstanceLinkClient.kt`
- `viewmodel/InstanceLinkViewModel.kt`
- `data/settings/InstanceLinkSettings.kt`
- `dialogs/InstanceLinkDialog.kt`, `dialogs/InstanceLinkToast.kt`
- `composables/ConnectionStatusRow.kt`

## Broadcast Integrations
- **Blackmagic ATEM** — upload animated lower thirds straight into the ATEM media pool and drive the upstream key automatically when you go live — one tap, perfectly timed.
- **OBS Studio** — automatically switch OBS scenes as your content changes, with per-content-type scene mapping.
- **Bitfocus Companion** — trigger lower thirds, ATEM keys and any content from a Stream Deck, either with ready-made HTTP buttons or a native Companion Satellite connection with live status right in the app.

**Source locations:**
- `atem/` (the `:atem` Gradle module, at the repo root) — the ATEM protocol client itself: `AtemClient`, `AtemConnectionManager`, `AtemFrameEncoder`, `AtemUploadStatus`
- `server/AtemBridge.kt` — the app-side wiring between that client, `AtemSettings` and the lower third
- `viewmodel/OBSWebSocketManager.kt`
- `tabs/CompanionSurfaceTab.kt`, `viewmodel/CompanionSatelliteViewModel.kt`, `composables/CompanionSurfacePanel.kt`, `composables/CompanionConnectionChipRow.kt`
- `companion-satellite/` (repository root) — native Companion Satellite protocol client
- `data/settings/AtemSettings.kt`, `data/settings/OBSSettings.kt`, `data/settings/CompanionSatelliteSettings.kt`
- `dialogs/tabs/AtemSettingsTab.kt`, `dialogs/tabs/OBSSettingsTab.kt`, `dialogs/tabs/CompanionSatelliteSettingsTab.kt`

## Reporting & Licensing
- **One statistics window** — every song and verse you present is tracked automatically, then reported in one place: songs, Bible and activity-over-time tabs over whichever period you pick.
- **Pick a period** — last 3, 6 or 12 months, any calendar year, all time, or an exact From/To range.
- **CCLI usage reports** — export date-filtered CSV/Excel for license reporting, with CCLI numbers resolved from your song library.
- **Tidy the numbers** — remove a single song or verse from the selected period, or clear everything; both ask first.

**Source locations:**
- `data/StatisticsManager.kt`
- `dialogs/CCLIReportDialog.kt` — the statistics window itself
- `data/StatisticsPeriod.kt` — the period presets shared by its pills and its date pickers

## Personalization & Workflow
- **34 languages** — full interface translation: English, Spanish, French, German, Portuguese, Dutch, Swedish, Norwegian, Finnish, Estonian, Latvian, Polish, Czech, Slovak, Croatian, Romanian, Ukrainian, Russian, Belarusian, Kazakh, Uzbek, Turkish, Arabic, Persian, Hindi, Nepali, Thai, Lao, Japanese, Chinese, Indonesian, Malay, Tagalog and Swahili — with the interface laid out right-to-left for Arabic and Persian.
- **9 themes** — light, dark, system and six accent themes to match your booth.
- **Make it yours** — View → Customize Theme… builds a whole palette from one accent color on a light or dark base, lets you set the background, text, secondary, selection, success, warning and error colors too — or leave any on Auto — and sets the font, text size and list Margin — Normal, Thin or Thinner, for more rows on screen — the app's own windows use. Text is kept readable whatever you pick, and output screens are never affected.
- **Guided setup** — a friendly first-run wizard gets your Bibles, songs and media ready in minutes.
- **Keyboard-driven** — comprehensive shortcuts for fast, mouse-free operation during a live service, every one of them rebindable from Help → Keyboard Shortcuts.
- **Tabs your way** — show the main tabs as icons, labels or both, with the margin between them set to taste.
- **Portable settings** — export and import your entire configuration to set up another machine instantly.
- **Stays running** — automatic update checks, crash recovery and launch-at-login keep things reliable.

**Source locations:**
- `ui/theme/LanguageProvider.kt`
- `theme/` (the `:theme` Gradle module, at the repo root) — `Theme.kt`, `ThemeManager.kt`, `ThemeCustomization.kt`, `SemanticColors.kt`, `AppThemeWrapper.kt`
- `dialogs/CustomizeThemeDialog.kt`, `dialogs/CustomizeThemePreview.kt`, `dialogs/ThemeCustomizationChoice.kt`, `ui/theme/ThemeCustomizationSettings.kt` — the Customize Theme window, its preview, what it hands back, and the settings it is read from
- `data/settings/CustomThemeColors.kt` (the `:settings` module) — the optional per-role colours
- `dialogs/SetupWizardDialog.kt`
- `dialogs/KeyboardShortcutsDialog.kt`, `dialogs/ShortcutBindingRow.kt`, `dialogs/ShortcutCapture.kt`, `dialogs/ShortcutCategoryRail.kt` — the shortcut list and rebinding
- `composables/LabeledTab.kt`, `dialogs/tabs/TabLabelsRow.kt` — tab label styles
- `tabs/CrosswordTab.kt`, `data/CrosswordData.kt` — a hidden tab (←→←→); `crossword/` (the `:crossword` Gradle module) is its authoring tool and the encoded puzzles
- `dialogs/OptionsDialog.kt`
- `data/SettingsManager.kt`, `data/settings/AppSettings.kt`, `data/settings/WindowLayoutSettings.kt`
- `utils/AutoStartManager.kt`, `utils/UpdateChecker.kt`
- `diagnostics/` (the `:diagnostics` Gradle module) — `CrashReporter`: crash logs and the Sentry bridge

## Free & Open
- **Free and open-source** — released under the GNU GPL v3. No subscriptions, no per-seat fees.
- **Cross-platform desktop** — built on Kotlin/Compose for Windows, macOS and Linux.
