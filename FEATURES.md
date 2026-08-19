# Church Presenter — Features

**Everything your church needs to put words on the screen — songs, scripture, slides, video, captions and broadcast graphics — in one free, open-source app.** 

> Source paths below are relative to `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/`

## Songs & Lyrics
- **Unlimited song library** — organize thousands of songs across as many songbooks as you like, indexed straight from a folder.
- **Powerful search** — find songs by title or number with contains, starts-with, exact-match and phrase filters, plus category and songbook filters.
- **Built-in song editor** — add and edit songs with simple verse/chorus formatting; no external tools needed.
- **Song Library Manager** — every song in one editable grid: search, filter by song book, show the columns you care about, type straight into a cell, and change a field on a whole selection at once. Renumbering a book, filling in missing composers or moving a set into a new song book is one screen rather than one song at a time.
- **Bilingual worship** — show two languages at once, side-by-side or stacked, or switch between primary and secondary on the fly.
- **Look-ahead for the band** — see the current and next section in advance so transitions stay smooth.
- **Favorites & play counts** — star the songs you use most and see how often each has been sung.
- **Bring your existing library** — the bundled converter reads SongBeamer, OpenLP (the `songs.sqlite` database itself or an OpenLyrics export), OpenSong, FreeShow, Free Worship, EasySlides, Quelea and SoftProjector libraries, plus lyrics pulled out of PDF, Word and PowerPoint files — several files or a whole folder at a time — and ready-to-use sample songs ship with the app.
- **Flexible display** — one verse or one line at a time, optional title slides, and full control over how numbers and titles appear.

**Source locations:**
- `tabs/SongsTab.kt` — main UI
- `viewmodel/SongsViewModel.kt`, `viewmodel/SongSettingsViewModel.kt`, `viewmodel/SongFolderWatcher.kt`
- `data/Songs.kt`, `data/SongItem.kt`, `data/SongFileParser.kt`, `data/SpsConverter.kt`
- `data/settings/SongSettings.kt`
- `presenter/SongPresenter.kt`
- `dialogs/EditSongDialog.kt`, `dialogs/tabs/SongSettingsTab.kt`
- `core-models/src/main/kotlin/.../models/LyricSection.kt` (the `:core-models` module)
- `converter/` (the `:converter` Gradle module, at the repo root) — format converter tool
- `songlibrary/` (the `:songlibrary` Gradle module) — the Song Library Manager grid
- `core-models/` (the `:core-models` Gradle module) — `core.models.songs`: the song, the `.song` file format and the library folder, shared by the app and the library window

## Bible & Scripture
- **Instant verse display** — browse any of the 66 books and put a verse on screen in seconds.
- **Dual and multi-translation modes** — keep the original primary/secondary workflow (including
  lower-third styling), or switch to an independent ordered stack of any number of translations.
  Multi-translation mode uses one full-screen typography profile per translation and has no
  lower-third-specific settings.
- **Multi-verse ranges** — select and present several verses at once with Ctrl/Shift click.
- **Search the whole Bible** — search across the entire text or just the current book.
- **History** — jump back to recently shown passages instantly.
- **Strong's dictionary** — explore original Hebrew and Greek words with transliteration, pronunciation, definitions and KJV usage.
- **Download translations in-app** — browse over 1,500 translations in more than 1,000 languages from eBible.org and the Zefania XML archive, filter by language, and install one in a click: it downloads and converts on your machine, straight into your Bible folder, with its copyright shown up front.
- **Follow along automatically** — connect a live speech-to-text feed and the app listens for spoken Bible references — stated outright or simply the next verse in a passage being read — and stages or goes live with the matching verse on its own, tiered by confidence so only clear matches jump straight to the screen.

**Source locations:**
- `tabs/BibleTab.kt` — main UI
- `tabs/DictionaryTab.kt` — Strong's dictionary UI
- `viewmodel/BibleViewModel.kt`, `viewmodel/BibleSettingsViewModel.kt`, `viewmodel/DictionaryViewModel.kt`
- `viewmodel/BibleEngineClient.kt` — auto-follow speech detection client
- `data/Bible.kt`, `data/BibleBook.kt`, `data/BibleBookNames.kt`, `data/BibleSearch.kt`, `data/BibleVerse.kt`, `data/StrongsEntry.kt`
- `data/BibleSource.kt`, `data/BibleInstallSupport.kt` — download sources: shared model and install machinery
- `data/EBibleSource.kt` — eBible.org catalogue (USFX), `data/ZefaniaSource.kt` + `data/ZefaniaRepositoryIndex.kt` — Zefania archive
- `viewmodel/BibleCatalogViewModel.kt`, `dialogs/BibleCatalogBrowserDialog.kt` — download browser UI
- `converter/src/main/kotlin/converter/bible/UsfxToSpbConverter.kt` — USFX conversion
- `data/settings/BibleSettings.kt`, `data/settings/BibleEngineSettings.kt`
- `presenter/BiblePresenter.kt`
- `dialogs/tabs/BibleSettingsTab.kt`
- `core-models/src/main/kotlin/.../models/SelectedVerse.kt` (the `:core-models` module)
- `appResources/common/ChurchPresenter-BLE/` — Bible Lookup Engine (speech-to-reference detection)

## Slides & Presentations
- **PowerPoint, Keynote & PDF** — drop in `.pptx`, `.ppt`, `.key` or `.pdf` files and present them as slides — no Microsoft or Apple software required.
- **Real animations, not just static slides** — PowerPoint and Keynote entrance, emphasis and exit effects, per-paragraph text builds, click-step sequencing, motion paths and slide transitions all play back live, right in the app.
- **Slide thumbnails & navigation** — see every slide at a glance and jump anywhere.
- **Presenter notes** — speaker notes from PowerPoint and Keynote flow straight to your stage monitor.

**Source locations:**
- `tabs/PresentationTab.kt` — main UI
- `viewmodel/PresentationViewModel.kt`
- `presenter/PresentationPlayer.kt`, `presenter/PresentationPresenter.kt` — animated playback
- `appResources/common/ChurchPresenter-PresentationEngine/` — PPTX/Keynote parsing, timing and animation engine
- `data/settings/PresentationSettings.kt`
- `server/CompanionServer.kt` — slide API for mobile (background rendering)

## Images & Media
- **Image slideshows** — point to a folder and present photos with crossfade, fade and slide transitions, auto-advance and looping.
- **Audio & video playback** — play local files or network streams (HTTP, RTSP and more), powered by VLC.
- **Full transport controls** — play, pause, seek, volume, mute, and choose your audio output device.
- **Background audio** — music keeps playing while you switch tabs or show other content.

**Source locations:**
- `tabs/PicturesTab.kt` — image slideshow UI
- `tabs/MediaTab.kt` — audio/video UI
- `viewmodel/PicturesViewModel.kt`, `viewmodel/MediaViewModel.kt`, `viewmodel/LocalMediaViewModel.kt`
- `data/settings/PictureSettings.kt`
- `presenter/PicturePresenter.kt`, `presenter/MediaPresenter.kt`
- `composables/VideoPlayer.kt`
- `dialogs/tabs/MediaSettingsTab.kt`

## Lower Thirds & Graphics
- **Animated lower thirds** — display polished Lottie animations for names, titles and welcomes.
- **Reusable presets** — save lower thirds with editable text fields and recall them instantly.
- **Built-in generator** — design your own animated lower thirds with the included Lottie generator — no After Effects needed.
- **Fine timing control** — pause on a frame, hold, and play through with smooth fade in/out.

**Source locations:**
- `tabs/LowerThird.kt` — main UI
- `viewmodel/LowerThirdSettingsViewModel.kt`
- `data/settings/LottiePreset.kt`, `data/settings/LottieSearchReplacePair.kt`
- `presenter/LowerThirdPresenter.kt`, `presenter/LowerThirdOffscreenRenderer.kt`
- `server/LowerThirdSequencer.kt`
- `dialogs/tabs/LowerThirdSettingsTab.kt`

## Announcements & Timers
- **On-screen announcements** — show text anywhere on screen with a wide range of slide and scroll animations, custom colors, speed and looping.
- **Countdown timers** — count down to a duration or to a specific clock time, with custom colors and an end-of-countdown message — perfect for "service starts in…".

**Source locations:**
- `tabs/AnnouncementsTab.kt` — main UI
- `viewmodel/AnnouncementsViewModel.kt`
- `data/settings/AnnouncementsSettings.kt`
- `presenter/AnnouncementsPresenter.kt`
- `utils/TimerStateManager.kt`

## Web & Canvas
- **Live websites on screen** — present any web page with bookmarks, navigation and zoom, and even type into live pages.
- **Canvas scene compositor** — build layered scenes from images, text, video, shapes, gradients, clocks, QR codes, live cameras, screen capture, web pages and Bible verses — like a mini production switcher inside the app.
- **QR codes made easy** — generate QR codes for URLs, WiFi, contact cards, email, SMS and more, right on the slide.

**Source locations:**
- `tabs/WebTab.kt` — web browser UI
- `tabs/CanvasTab.kt` — scene compositor UI
- `viewmodel/SceneViewModel.kt`
- `core-models/src/main/kotlin/.../models/SceneModels.kt` (the `:core-models` module)
- `composables/SceneCanvas.kt`, `composables/SceneSourceRenderer.kt`, `composables/SourcePropertiesPanel.kt`
- `composables/SharedBrowserFrameCache.kt`, `composables/SharedCameraFrameCache.kt`
- `presenter/ScenePresenter.kt`, `presenter/WebsitePresenter.kt`
- `data/settings/WebBookmark.kt`

## Live Captions & Translation
- **Real-time captions** — connect a speech-to-text server to caption your service live.
- **Live translation** — show transcription, translation, or both together in stacked or side-by-side layouts.

**Source locations:**
- `tabs/STTTab.kt` — main UI
- `viewmodel/STTManager.kt`
- `data/settings/STTSettings.kt`
- `presenter/STTPresenter.kt`

## Audience Q&A
- **Questions from the congregation** — people scan a QR code and submit questions from their phones.
- **Full moderation** — approve, deny, sort and queue questions before any go live.
- **Anywhere access** — optional public access lets people ask over mobile data without joining your WiFi.
- **Voting & history** — let the room upvote approved questions, and export the session afterward.

**Source locations:**
- `tabs/QATab.kt` — main UI
- `viewmodel/QAManager.kt`
- `data/settings/QASettings.kt`
- `presenter/QAPresenter.kt`
- `core-models/src/main/kotlin/.../models/Question.kt` (the `:core-models` module)

## Service Planning
- **Drag-and-drop schedules** — build your whole service from songs, scripture, slides, media, lower thirds, announcements and websites.
- **Save & reopen services** — store schedules as files and pick up exactly where you left off, with autosave and crash recovery.
- **Stay organized** — color-coded labels, per-item notes, quick reordering, recents and full undo/redo.

**Source locations:**
- `tabs/ScheduleTab.kt` — main UI
- `viewmodel/ScheduleViewModel.kt`
- `core-models/src/main/kotlin/.../models/ScheduleItem.kt` (the `:core-models` module)
- `viewmodel/FileManager.kt`
- `dialogs/AddLabelDialog.kt`

## Projection & Output
- **Unlimited outputs** — drive as many screens as you have — one window per connected display, plus every DeckLink/SDI device. No artificial limit.
- **Full screen or lower third** — present full-screen or as a lower-third band, per content type.
- **Beautiful backgrounds** — solid colors, images, looping video, gradients or transparent — set defaults and per-type overrides.
- **Built-in stock photo & video search** — search and download from Pexels and Pixabay right inside the app with a free API key, plus a set of preloaded backgrounds ready to use offline.
- **Broadcast fill + key** — output separate fill and key signals for hardware keying, including SDI via Blackmagic DeckLink.
- **Browser Source streaming output** — a transparent, OBS-ready browser-source overlay with true alpha transparency, crossfaded mode switching and configurable per-output resolution/fps — for lower thirds, media, websites and more, no OBS scene-switching integration required.
- **Typography that fits** — auto-fit text to the screen, with control over fonts, size, alignment, shadows and margins.
- **Live preview** — always see exactly what's on screen, and lock any output to a chosen tab.

**Source locations:**
- `PresenterScreen.kt` — output window
- `presenter/Presenting.kt` — active-content state enum
- `presenter/DeckLinkComposeOutput.kt`
- `presenter/BrowserSourceVideoRenderer.kt`, `presenter/LocalTransparentBlanking.kt` — Browser Source output
- `data/StockMediaClient.kt`, `dialogs/StockMediaBrowserDialog.kt`, `viewmodel/StockMediaViewModel.kt`, `data/settings/StockPhotoSettings.kt`
- `composables/DeckLinkIO.kt`, `composables/LivePreviewPanel.kt`, `composables/LoopingVideoBackground.kt`
- `viewmodel/PresenterManager.kt`, `viewmodel/BackgroundSettingsViewModel.kt`
- `data/settings/BackgroundConfig.kt`, `data/settings/BackgroundSettings.kt`, `data/settings/ProjectionSettings.kt`, `data/settings/ScreenAssignment.kt`
- `dialogs/tabs/BackgroundSettingsTab.kt`, `dialogs/tabs/ProjectionSettingsTab.kt`
- `utils/AutoFitUtils.kt`

## Stage Monitor
- **Confidence display for the platform** — give worship leaders and speakers their own screen showing the current slide, next slide, a clock, the countdown timer, section labels and presenter notes — in vertical, horizontal or four-quadrant layouts.

**Source locations:**
- `StageMonitorScreen.kt`
- `data/settings/StageMonitorSettings.kt`
- `dialogs/tabs/StageMonitorSettingsTab.kt`

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
- `server/AtemClient.kt`, `server/AtemConnectionManager.kt`, `server/AtemFrameEncoder.kt`, `server/AtemRenderCache.kt`, `server/AtemUploadStatus.kt`
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
- **Guided setup** — a friendly first-run wizard gets your Bibles, songs and media ready in minutes.
- **Keyboard-driven** — comprehensive shortcuts for fast, mouse-free operation during a live service.
- **Portable settings** — export and import your entire configuration to set up another machine instantly.
- **Stays running** — automatic update checks, crash recovery and launch-at-login keep things reliable.

**Source locations:**
- `ui/theme/LanguageProvider.kt`
- `theme/` (the `:theme` Gradle module, at the repo root) — `Theme.kt`, `ThemeManager.kt`, `SemanticColors.kt`, `AppThemeWrapper.kt`
- `dialogs/SetupWizardDialog.kt`
- `dialogs/KeyboardShortcutsDialog.kt`
- `dialogs/OptionsDialog.kt`
- `data/SettingsManager.kt`, `data/settings/AppSettings.kt`, `data/settings/WindowLayoutSettings.kt`
- `utils/AutoStartManager.kt`, `utils/UpdateChecker.kt`, `utils/CrashReporter.kt`

## Free & Open
- **Free and open-source** — released under the GNU GPL v3. No subscriptions, no per-seat fees.
- **Cross-platform desktop** — built on Kotlin/Compose for Windows, macOS and Linux.
