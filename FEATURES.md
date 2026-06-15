# Church Presenter — Features

**Everything your church needs to put words on the screen — songs, scripture, slides, video, captions and broadcast graphics — in one free, open-source app.** 

> Source paths below are relative to `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/`

## Songs & Lyrics
- **Unlimited song library** — organize thousands of songs across as many songbooks as you like, indexed straight from a folder.
- **Powerful search** — find songs by title or number with contains, starts-with, exact-match and phrase filters, plus category and songbook filters.
- **Built-in song editor** — add and edit songs with simple verse/chorus formatting; no external tools needed.
- **Bilingual worship** — show two languages at once, side-by-side or stacked, or switch between primary and secondary on the fly.
- **Look-ahead for the band** — see the current and next section in advance so transitions stay smooth.
- **Favorites & play counts** — star the songs you use most and see how often each has been sung.
- **Bring your existing library** — imports OpenLyrics, SongPro and ChurchPresenter formats, with a bundled converter and ready-to-use sample songs.
- **Flexible display** — one verse or one line at a time, optional title slides, and full control over how numbers and titles appear.

**Source locations:**
- `tabs/SongsTab.kt` — main UI
- `viewmodel/SongsViewModel.kt`, `viewmodel/SongSettingsViewModel.kt`, `viewmodel/SongFolderWatcher.kt`
- `data/Songs.kt`, `data/SongItem.kt`, `data/SongFileParser.kt`, `data/SpsConverter.kt`
- `data/settings/SongSettings.kt`
- `presenter/SongPresenter.kt`
- `dialogs/EditSongDialog.kt`, `dialogs/tabs/SongSettingsTab.kt`
- `models/LyricSection.kt`
- `appResources/common/ChurchPresenter-Converter/` — format converter tool

## Bible & Scripture
- **Instant verse display** — browse any of the 66 books and put a verse on screen in seconds.
- **Dual translations** — show a primary and secondary Bible together, and swap them with one click.
- **Multi-verse ranges** — select and present several verses at once with Ctrl/Shift click.
- **Search the whole Bible** — search across the entire text or just the current book.
- **History** — jump back to recently shown passages instantly.
- **Strong's dictionary** — explore original Hebrew and Greek words with transliteration, pronunciation, definitions and KJV usage.

**Source locations:**
- `tabs/BibleTab.kt` — main UI
- `tabs/DictionaryTab.kt` — Strong's dictionary UI
- `viewmodel/BibleViewModel.kt`, `viewmodel/BibleSettingsViewModel.kt`, `viewmodel/DictionaryViewModel.kt`
- `data/Bible.kt`, `data/BibleBook.kt`, `data/BibleBookNames.kt`, `data/BibleSearch.kt`, `data/BibleVerse.kt`, `data/StrongsEntry.kt`
- `data/settings/BibleSettings.kt`
- `presenter/BiblePresenter.kt`
- `dialogs/tabs/BibleSettingsTab.kt`
- `models/SelectedVerse.kt`

## Slides & Presentations
- **PowerPoint, Keynote & PDF** — drop in `.pptx`, `.ppt`, `.key` or `.pdf` files and present them as slides — no Microsoft or Apple software required.
- **Slide thumbnails & navigation** — see every slide at a glance and jump anywhere.
- **Presenter notes** — speaker notes from PowerPoint and Keynote flow straight to your stage monitor.

**Source locations:**
- `tabs/PresentationTab.kt` — main UI
- `viewmodel/PresentationViewModel.kt`
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
- `models/SceneModels.kt`
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
- `models/Question.kt`

## Service Planning
- **Drag-and-drop schedules** — build your whole service from songs, scripture, slides, media, lower thirds, announcements and websites.
- **Save & reopen services** — store schedules as files and pick up exactly where you left off, with autosave and crash recovery.
- **Stay organized** — color-coded labels, per-item notes, quick reordering, recents and full undo/redo.

**Source locations:**
- `tabs/ScheduleTab.kt` — main UI
- `viewmodel/ScheduleViewModel.kt`
- `models/ScheduleItem.kt`
- `viewmodel/FileManager.kt`
- `dialogs/AddLabelDialog.kt`

## Projection & Output
- **Unlimited outputs** — drive as many screens as you have — one window per connected display, plus every DeckLink/SDI device. No artificial limit.
- **Full screen or lower third** — present full-screen or as a lower-third band, per content type.
- **Beautiful backgrounds** — solid colors, images, looping video, gradients or transparent — set defaults and per-type overrides.
- **Broadcast fill + key** — output separate fill and key signals for hardware keying, including SDI via Blackmagic DeckLink.
- **Typography that fits** — auto-fit text to the screen, with control over fonts, size, alignment, shadows and margins.
- **Live preview** — always see exactly what's on screen, and lock any output to a chosen tab.

**Source locations:**
- `PresenterScreen.kt` — output window
- `presenter/Presenting.kt` — active-content state enum
- `presenter/DeckLinkComposeOutput.kt`
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

## Broadcast Integrations
- **Blackmagic ATEM** — upload animated lower thirds straight into the ATEM media pool and drive the upstream key automatically when you go live — one tap, perfectly timed.
- **OBS Studio** — automatically switch OBS scenes as your content changes, with per-content-type scene mapping.
- **Bitfocus Companion** — trigger lower thirds, ATEM keys and any content from a Stream Deck with ready-made HTTP buttons.

**Source locations:**
- `server/AtemClient.kt`, `server/AtemConnectionManager.kt`, `server/AtemFrameEncoder.kt`, `server/AtemRenderCache.kt`, `server/AtemUploadStatus.kt`
- `viewmodel/OBSWebSocketManager.kt`
- `data/settings/AtemSettings.kt`, `data/settings/OBSSettings.kt`
- `dialogs/tabs/AtemSettingsTab.kt`, `dialogs/tabs/OBSSettingsTab.kt`

## Reporting & Licensing
- **CCLI usage reports** — automatically track every song and verse you present and export date-filtered CSV/Excel reports for license reporting.
- **Statistics & charts** — see your most-used songs and passages over any time period.

**Source locations:**
- `data/StatisticsManager.kt`
- `dialogs/CCLIReportDialog.kt`
- `dialogs/StatisticsDialog.kt`, `dialogs/tabs/StatisticsTab.kt`

## Personalization & Workflow
- **14 languages** — full interface translation including English, Spanish, French, German, Portuguese, Dutch, Polish, Czech, Slovak, Romanian, Ukrainian, Russian, Belarusian and Kazakh.
- **9 themes** — light, dark, system and six accent themes to match your booth.
- **Guided setup** — a friendly first-run wizard gets your Bibles, songs and media ready in minutes.
- **Keyboard-driven** — comprehensive shortcuts for fast, mouse-free operation during a live service.
- **Portable settings** — export and import your entire configuration to set up another machine instantly.
- **Stays running** — automatic update checks, crash recovery and launch-at-login keep things reliable.

**Source locations:**
- `ui/theme/Theme.kt`, `ui/theme/ThemeManager.kt`, `ui/theme/LanguageProvider.kt`, `ui/theme/AppThemeWrapper.kt`
- `dialogs/SetupWizardDialog.kt`
- `dialogs/KeyboardShortcutsDialog.kt`
- `dialogs/OptionsDialog.kt`
- `data/SettingsManager.kt`, `data/settings/AppSettings.kt`, `data/settings/WindowLayoutSettings.kt`
- `utils/AutoStartManager.kt`, `utils/UpdateChecker.kt`, `utils/CrashReporter.kt`

## Free & Open
- **Free and open-source** — released under the GNU GPL v3. No subscriptions, no per-seat fees.
- **Cross-platform desktop** — built on Kotlin/Compose for Windows, macOS and Linux.
