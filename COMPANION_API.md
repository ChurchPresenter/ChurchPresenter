# ChurchPresenter Companion API

REST + WebSocket API exposed by the desktop app for the mobile companion.

---

## Base URL

| Protocol | Address | Notes |
|----------|---------|-------|
| HTTP | `http://<desktop-ip>:<port>` | Default port **8765** — used by all devices |
| HTTP  | `http://127.0.0.1:<port+1>` | e.g. `8766` — localhost only (embedded WebView) |

The server displays its full URL in **Settings → Server**.  
Replace `http://192.168.1.10:8765` with the URL shown there in every sample below.

> **No SSL certificate required** — the server uses plain HTTP and plain WebSocket (`ws://`) over the local network.

---

## Authentication

Every route checks one of five things, named in the **Auth** column of the [route index](#route-index):

| Auth | How it is checked | Fails with |
|------|-------------------|------------|
| **API key** | Only when the API key is enabled in Settings → Server *and* not blank; otherwise open. Send `X-Api-Key: <key>` or `?apiKey=<key>`. | `401 Invalid API key` (WebSocket: one `{"error":"Unauthorized"}` frame, then the session ends) |
| **Output key** | Browser Source routes only: the API key, but only when that output has *Require API key* switched on and a key is set — the global enable switch does not apply. | `401 Invalid API key` (WebSocket: closed with `VIOLATED_POLICY`) |
| **Presentation password** | Presentation remote only. The remote must be enabled (else `403 {"error":"remote control is disabled"}`); when a password is set, send `X-Presentation-Password: <pw>` or `?password=<pw>`. | `401 {"error":"Invalid password"}` |
| **Q&A admin password** | Q&A moderation only. When an admin password is set, send `X-QA-Password: <pw>` or `?password=<pw>`. | `401 {"error":"Invalid admin password"}` |
| **Open** | No check at all: the public Q&A and remote pages, and the CA certificate downloads. | — |

If the API key is wrong the server responds `HTTP 401 Unauthorized`.

**Dev mode only.** Routes marked *dev* in the index answer `403 {"ok":false,"reason":"dev mode only"}`
while the desktop is not in dev mode (WebSocket commands are acked `ok:false`, reason
`dev_mode_only`). They are features not yet approved for production.

**Operator approval.** Some routes also wait for the desktop operator to click **Allow** before they
act (marked *approval* in the index). A device the operator has trusted is approved without a
prompt, and a blocked device is refused without one.

```bash
# Header
curl -H "X-Api-Key: mysecretkey" http://192.168.1.10:8765/api/info

# Query param
curl "http://192.168.1.10:8765/api/info?apiKey=mysecretkey"
```

### Device Identification (Optional)

Pass `X-Device-Id` on action requests (`/api/schedule/add`, `/api/schedule/add-batch`, `/api/project`) to identify your device in the approval dialog shown on the desktop.

```bash
curl -k -X POST https://192.168.1.10:8765/api/schedule/add \
  -H "X-Device-Id: MyiPhone" \
  -H "Content-Type: application/json" \
  -d '{"item":{"songNumber":42,"title":"Great Is Thy Faithfulness","songbook":"Hymns"}}'
```

---

## Route Index

Every HTTP and WebSocket route the server registers (87). Paths are written exactly as the server
registers them; `{name}` is a path parameter.

### Info, songs and schedule

| Route | Auth | Dev mode | Notes |
|-------|------|----------|-------|
| `GET /api/info` | API key | — | Name, version, port |
| `GET /api/status` | API key | — | Capabilities and permissions |
| `GET /api/song-catalog` | API key | — | Songbooks with each song's usual length |
| `GET /api/songs` | API key | — | Song catalog |
| `GET /api/songs/{identifier}` | API key | — | One song with its sections |
| `POST /api/songs/{number}/select` | API key | — | Jump to a section of the live song |
| `GET /api/schedule` | API key | — | The schedule |
| `POST /api/schedule/add` | API key | — | *approval* |
| `POST /api/schedule/add-batch` | API key | — | *approval* |
| `POST /api/project` | API key | — | *approval* |
| `POST /api/clear` | API key | `layer=` and `group=` only | Clearing everything is not dev mode |
| `POST /api/take` | API key | dev | |
| `POST /api/message` | API key | dev | |
| `GET /api/props` | API key | dev | |
| `POST /api/props/{id}/{action}` | API key | dev | `{action}` is `on`, `off` or `toggle` |
| `GET /api/clear-groups` | API key | dev | |
| `GET /api/macros` | API key | dev | |
| `POST /api/macro/{name}` | API key | dev | |

### Bible and dictionary

| Route | Auth | Dev mode | Notes |
|-------|------|----------|-------|
| `GET /api/bible` | API key | — | Catalog, or a chapter's text |
| `POST /api/bible/select` | API key | — | Show a verse now |
| `GET /api/bible/file` | API key | — | Primary `.spb` file (Instance Link) |
| `GET /api/bible/file/secondary` | API key | — | Secondary `.spb` file (Instance Link) |
| `GET /api/bible/file/translations` | API key | — | Ordered module file names |
| `GET /api/bible/file/translation/{index}` | API key | — | One module by manifest position |
| `GET /api/dictionary` | API key | — | Strong's search |
| `GET /api/dictionary/{number}` | API key | — | One Strong's entry |
| `GET /api/dictionary/{number}/verses` | API key | — | Verses a Strong's number appears in |

### Presentations, pictures, media and backgrounds

| Route | Auth | Dev mode | Notes |
|-------|------|----------|-------|
| `GET /api/presentations` | API key | — | |
| `GET /api/presentations/{id}` | API key | — | |
| `GET /api/presentations/{id}/slides/{index}` | API key | — | JPEG |
| `POST /api/presentations/{id}/select` | API key | — | |
| `POST /api/presentations/upload` | API key | — | Needs file upload enabled |
| `GET /api/pictures` | API key | — | |
| `GET /api/pictures/{id}` | API key | — | |
| `GET /api/pictures/{id}/images/{index}` | API key | — | Image bytes |
| `POST /api/pictures/select` | API key | — | |
| `POST /api/pictures/upload` | API key | — | Needs file upload enabled |
| `POST /api/media/upload` | API key | — | Needs file upload enabled; raw bytes |
| `GET /api/media/stream/{id}` | API key | — | Range requests supported |
| `GET /api/backgrounds` | API key | — | Background settings JSON |
| `GET /api/backgrounds/asset/{slot}` | API key | — | Background image/video bytes |

### Presentation remote

| Route | Auth | Dev mode | Notes |
|-------|------|----------|-------|
| `GET /presentation-remote` | Open | — | The remote's web page |
| `GET /api/presentation-remote/status` | Open | — | |
| `POST /api/presentation-remote/auth` | Presentation password | — | *approval* (once per device) |
| `POST /api/presentation-remote/next` | Presentation password | — | |
| `POST /api/presentation-remote/previous` | Presentation password | — | |
| `POST /api/presentation-remote/goto/{index}` | Presentation password | — | |
| `POST /api/presentation-remote/freeze` | Presentation password | — | |
| `POST /api/presentation-remote/play-pause` | Presentation password | — | |
| `POST /api/presentation-remote/loop` | Presentation password | — | |
| `POST /api/presentation-remote/go-live` | Presentation password | — | |
| `POST /api/presentation-remote/upload` | Presentation password | — | |

### Q&A

| Route | Auth | Dev mode | Notes |
|-------|------|----------|-------|
| `GET /qa` | Open | — | Submission page |
| `GET /qa/admin` | Open | — | Admin page (its calls are password-checked) |
| `GET /qa/vote` | Open | — | Voting page |
| `GET /api/qa/status` | Open | — | |
| `POST /api/qa/submit` | Open | — | Session must be active; rate-limited |
| `GET /api/qa/approved` | Open | — | Voting must be enabled |
| `POST /api/qa/vote` | Open | — | Voting must be enabled |
| `POST /api/qa/auth` | Q&A admin password | — | *approval* (once per device) |
| `GET /api/qa/questions` | Q&A admin password | — | |
| `POST /api/qa/questions/{id}/approve` | Q&A admin password | — | *approval* |
| `POST /api/qa/questions/{id}/edit` | Q&A admin password | — | *approval* |
| `POST /api/qa/questions/{id}/deny` | Q&A admin password | — | *approval* |
| `POST /api/qa/questions/{id}/done` | Q&A admin password | — | *approval* |
| `POST /api/qa/questions/{id}/display` | Q&A admin password | — | *approval* |
| `DELETE /api/qa/questions/{id}` | Q&A admin password | — | *approval* |
| `POST /api/qa/add` | Q&A admin password | — | *approval* |
| `POST /api/qa/clear-display` | Q&A admin password | — | *approval* |
| `POST /api/qa/clear-all` | Q&A admin password | — | No approval |

### Lower thirds and ATEM

| Route | Auth | Dev mode | Notes |
|-------|------|----------|-------|
| `GET /api/lowerthirds` | API key | — | |
| `GET /api/lowerthirds/{name}/json` | API key | — | Raw Lottie JSON |
| `POST /api/lowerthirds/{name}/run` | API key | — | |
| `POST /api/lowerthirds/{name}/show` | API key | — | |
| `POST /api/lowerthirds/hide` | API key | — | |
| `POST /api/atem/still/{name}` | API key | — | Upload to the media pool |
| `POST /api/atem/clip/{name}` | API key | — | Upload to the media pool |
| `POST /api/atem/key/on` | API key | — | |
| `POST /api/atem/key/off` | API key | — | |

### Browser Source

| Route | Auth | Dev mode | Notes |
|-------|------|----------|-------|
| `GET /browser-source/{index}` | Output key | — | The overlay page; `{index}` counts from 1 |
| `WS /api/browser-source/{index}/ws` | Output key | — | Binary frame stream |
| `POST /api/browser-source/{index}/auth` | Output key | — | *approval* (once per device) |
| `POST /api/browser-source/{index}/transpose` | Output key | — | Approved device only |

### Calendar, certificates and the WebSocket

| Route | Auth | Dev mode | Notes |
|-------|------|----------|-------|
| `POST /api/calendar/enroll` | API key | — | *approval* |
| `GET /ca.crt` | Open | — | DER CA certificate |
| `GET /ca.pem` | Open | — | PEM CA certificate |
| `WS /ws` | API key | — | The companion event/command stream |

---

## Read Endpoints (GET)

### `GET /api/info`

Returns app name, version, and active port.

```bash
curl -k https://192.168.1.10:8765/api/info
```

```json
{
  "name": "ChurchPresenter",
  "version": "1.0.0",
  "port": 8765
}
```

---

### `GET /api/status`

Returns server capability and per-device permission information.  
Called by the mobile companion immediately after launch to warn users about limited functionality.

The server resolves `permissions` based on the `X-Device-Id` header.  
If the header is absent or the device has no explicit entry, global defaults are used.

```bash
curl -H "X-Device-Id: MyiPhone" http://192.168.1.10:8765/api/status
```

```json
{
  "appVersion": "1.4.2",
  "endpoints": ["songs", "bible", "schedule", "presentations", "pictures", "status"],
  "bibles": ["KJV"],
  "songbooks": ["Hymns", "Contemporary"],
  "permissions": {
    "canPresent": true,
    "canAddToSchedule": true,
    "canUploadFiles": false,
    "maxMediaUploadMb": 700
  }
}
```

The response also carries the `X-Server-Version` header.

| Field | Type | Description |
|-------|------|-------------|
| `appVersion` | `string` | Desktop app version string, e.g. `"1.4.2"` |
| `endpoints` | `string[]` | List of API path segments this server exposes |
| `bibles` | `string[]` | The loaded translation's name (empty array if none) |
| `songbooks` | `string[]` | Song-book names loaded in the app (empty array if none) |
| `permissions.canPresent` | `bool` | Whether this device may call `/api/project` and `/api/clear` (always `true` today) |
| `permissions.canAddToSchedule` | `bool` | Whether this device may call `/api/schedule/add` (always `true` today) |
| `permissions.canUploadFiles` | `bool` | Whether file upload is enabled — `/api/presentations/upload`, `/api/pictures/upload`, `/api/media/upload` |
| `permissions.maxMediaUploadMb` | `int` | The largest file `POST /api/media/upload` accepts, in MB |

> All `permissions` fields default to `true` when absent so that older server versions that omit the object do not incorrectly restrict devices.

---

### `GET /api/songs`

Returns the full song catalog grouped by songbook.

```bash
curl -k https://192.168.1.10:8765/api/songs
```

**Optional query param** — filter to one songbook:

```bash
curl -k "https://192.168.1.10:8765/api/songs?songbook=Hymns"
```

```json
{
  "song-book": [
    {
      "book-name": "Hymns",
      "song-total": 3,
      "songs": [
        { "number": "1",  "title": "Amazing Grace",    "tune": "NEW BRITAIN", "author": "John Newton" },
        { "number": "42", "title": "Great Is Thy Faithfulness", "tune": "", "author": "Thomas Chisholm" },
        { "number": "85", "title": "How Great Thou Art", "tune": "O STORE GUD", "author": "Carl Boberg" }
      ]
    }
  ],
  "songBooks": 1,
  "total": 3
}
```

---

### `GET /api/song-catalog`

The songbooks, with each song's usual length, for planning a service:
`{"books": [ ... ]}`. Fields that are empty are left out rather than sent as `null`.

---

### `GET /api/songs/{identifier}`

Returns full song detail including all lyric sections. `{identifier}` is the song number (`_` for
a song with no number). Optional query params: `songbook=` narrows to one songbook, `title=` also
matches by title (any case), and `id=N` looks the song up by its index in the library instead.
`404 {"error":"song not found"}` when nothing matches.

```bash
curl -k https://192.168.1.10:8765/api/songs/42
```

**Optional query param** — disambiguate when the same number exists in multiple songbooks:

```bash
curl -k "https://192.168.1.10:8765/api/songs/42?songbook=Hymns"
```

```json
{
  "number": "42",
  "title": "Great Is Thy Faithfulness",
  "songbook": "Hymns",
  "tune": "",
  "author": "Thomas Chisholm",
  "composer": "",
  "section-total": 4,
  "sections": [
    {
      "type": "verse",
      "lines": [
        "Great is Thy faithfulness, O God my Father",
        "There is no shadow of turning with Thee"
      ]
    },
    {
      "type": "chorus",
      "lines": [
        "Great is Thy faithfulness!",
        "Great is Thy faithfulness!"
      ]
    }
  ]
}
```

Section `type` values: `"verse"` · `"chorus"` · `"other"`

---

### `GET /api/bible`

Returns the full Bible catalog (all books with chapter and verse counts). No verse text is included here.

Instance Link also mirrors the complete ordered translation stack through:

- `GET /api/bible/file/translations` — ordered module file-name manifest.
- `GET /api/bible/file/translation/{index}` — raw `.spb` module at that manifest position.

The existing `/api/bible/file` and `/api/bible/file/secondary` routes remain available for older
followers.

```bash
curl -k https://192.168.1.10:8765/api/bible
```

```json
{
  "translation": "KJV",
  "books": [
    {
      "book-id": 1,
      "book-name": "Genesis",
      "chapter-total": 50,
      "chapters": [
        { "chapter": 1, "verse-total": 31 },
        { "chapter": 2, "verse-total": 25 }
      ]
    },
    {
      "book-id": 43,
      "book-name": "John",
      "chapter-total": 21,
      "chapters": [
        { "chapter": 1,  "verse-total": 51 },
        { "chapter": 3,  "verse-total": 36 }
      ]
    }
  ],
  "book-total": 66,
  "verse-total": 31102
}
```

**Optional filters:**

```bash
# Filter to one book by name
curl -k "https://192.168.1.10:8765/api/bible?book=John"

# Filter to one book by numeric id
curl -k "https://192.168.1.10:8765/api/bible?book=43"
```

---

### `GET /api/bible?book={id}&chapter={num}`

Returns a **full chapter** with verse text.  
`book` must be the numeric `book-id` from the catalog.

```bash
curl -k "https://192.168.1.10:8765/api/bible?book=43&chapter=3"
```

```json
{
  "translation": "KJV",
  "book-id": 43,
  "book-name": "John",
  "chapter": 3,
  "verse-total": 36,
  "verses": [
    { "verse": 1,  "text": "There was a man of the Pharisees, named Nicodemus…" },
    { "verse": 16, "text": "For God so loved the world, that he gave his only begotten Son…" },
    { "verse": 17, "text": "For God sent not his Son into the world to condemn the world…" }
  ]
}
```

---

### `GET /api/schedule`

Returns the current schedule as an ordered list of items.

```bash
curl -k https://192.168.1.10:8765/api/schedule
```

```json
{
  "items": [
    {
      "id": "a1b2c3",
      "type": "song",
      "displayText": "42 - Great Is Thy Faithfulness",
      "songNumber": 42,
      "title": "Great Is Thy Faithfulness",
      "songbook": "Hymns"
    },
    {
      "id": "d4e5f6",
      "type": "bible",
      "displayText": "John 3:16",
      "bookName": "John",
      "chapter": 3,
      "verseNumber": 16,
      "text": "For God so loved the world…"
    },
    {
      "id": "e5f6g7",
      "type": "bible",
      "displayText": "Genesis 1:1-3",
      "bookName": "Genesis",
      "chapter": 1,
      "verseNumber": 1,
      "verseRange": "1-3",
      "text": "In the beginning God created…"
    },
    {
      "id": "g7h8i9",
      "type": "picture",
      "displayText": "Easter 2026 (12 images)",
      "folderPath": "/Users/…/Easter2026",
      "folderName": "Easter 2026",
      "imageCount": 12
    }
  ],
  "total": 4
}
```

> For **single-verse** Bible items `verseRange` is omitted (null). For **multi-verse** items it contains the range string, e.g. `"1-3"` or `"2,4"`. `verseNumber` is always the first verse in the range.

`type` values: `song` · `bible` · `label` · `picture` · `presentation` · `media` · `lower_third` · `announcement` · `website`

---

### `GET /api/presentations`

Returns loaded presentation metadata (slides are fetched individually).

```bash
curl -k https://192.168.1.10:8765/api/presentations
```

```json
{
  "presentations": [
    {
      "id": "uuid-1234",
      "file-name": "EasterSermon.pptx",
      "file-type": "pptx",
      "slide-total": 5,
      "slides": [
        { "slide-index": 0, "thumbnail-url": "/api/presentations/uuid-1234/slides/0" },
        { "slide-index": 1, "thumbnail-url": "/api/presentations/uuid-1234/slides/1" }
      ]
    }
  ],
  "total": 1
}
```

---

### `GET /api/presentations/{id}/slides/{index}`

Returns a single slide as a **JPEG image**.

```bash
curl -k -o slide0.jpg \
  https://192.168.1.10:8765/api/presentations/uuid-1234/slides/0
```

```swift
// Swift / iOS
let url = URL(string: "https://192.168.1.10:8765/api/presentations/uuid-1234/slides/0")!
let (data, _) = try await URLSession.shared.data(from: url)
let image = UIImage(data: data)
```

---

### `GET /api/presentations/{id}`

Returns metadata for **any** presentation by its ID — no need to open it in the Presentations tab first.

The `{id}` is either:
- The schedule item `id` from `GET /api/schedule` (works for every presentation item; slides are rendered in the background when the schedule is loaded), or
- The presentation ID (`"id"` field) returned by `GET /api/presentations`.

```bash
# Using a schedule item id directly
curl -k https://192.168.1.10:8765/api/presentations/550e8400-e29b-41d4-a716-446655440000
```

```json
{
  "id": "3f2a1b4c",
  "file-name": "EasterSermon",
  "file-type": "pptx",
  "slide-total": 5,
  "slides": [
    { "slide-index": 0, "thumbnail-url": "/api/presentations/3f2a1b4c/slides/0" },
    { "slide-index": 1, "thumbnail-url": "/api/presentations/3f2a1b4c/slides/1" },
    { "slide-index": 2, "thumbnail-url": "/api/presentations/3f2a1b4c/slides/2" }
  ]
}
```

> Slides are rendered in the background when the schedule is received. A request made in the first few seconds after `schedule_updated` may briefly return `404` while rendering is in progress — simply retry.

---

### `GET /api/pictures`

Returns the loaded picture folder metadata.

```bash
curl -k https://192.168.1.10:8765/api/pictures
```

```json
{
  "folder-id": "a1b2c3d4",
  "folder-name": "Easter 2026",
  "folder-path": "/Users/…/Easter2026",
  "image-total": 3,
  "images": [
    { "index": 0, "file-name": "img001.jpg", "thumbnail-url": "/api/pictures/a1b2c3d4/images/0" },
    { "index": 1, "file-name": "img002.jpg", "thumbnail-url": "/api/pictures/a1b2c3d4/images/1" },
    { "index": 2, "file-name": "img003.jpg", "thumbnail-url": "/api/pictures/a1b2c3d4/images/2" }
  ]
}
```

---

### `GET /api/pictures/{id}/images/{index}`

Returns a single picture as a **JPEG/PNG image**.

```bash
curl -k -o img001.jpg \
  https://192.168.1.10:8765/api/pictures/a1b2c3d4/images/0
```

---

### `GET /api/pictures/{id}`

Returns catalog metadata for **any** picture folder by its ID — no need to load or project it first.

The `{id}` is either:
- The schedule item `id` from `GET /api/schedule` (works for every picture item as soon as the schedule is received), or
- The `folder-id` from `GET /api/pictures` (the currently active folder in the Pictures tab).

```bash
# Using a schedule item id directly
curl -k https://192.168.1.10:8765/api/pictures/56337f54-3b4f-4b05-92d2-c99ea2b2a50b
```

```json
{
  "folder-id": "56337f54-3b4f-4b05-92d2-c99ea2b2a50b",
  "folder-name": "For grandma",
  "folder-path": "/Users/andreichernyshev/Desktop/For grandma",
  "image-total": 132,
  "images": [
    { "index": 0, "file-name": "IMG_0001.jpg", "thumbnail-url": "/api/pictures/56337f54-3b4f-4b05-92d2-c99ea2b2a50b/images/0" },
    { "index": 1, "file-name": "IMG_0002.jpg", "thumbnail-url": "/api/pictures/56337f54-3b4f-4b05-92d2-c99ea2b2a50b/images/1" }
  ]
}
```

> The catalog is indexed in the background when the schedule is loaded. A request made in the first few milliseconds after receiving `schedule_updated` may briefly return `404` — simply retry.

---

## Action Endpoints (POST)

`POST /api/schedule/add`, `POST /api/schedule/add-batch` and `POST /api/project` **suspend** until
the desktop user clicks **Allow** or **Deny** in the permission dialog (a trusted device is allowed
without one). The HTTP connection stays open until that decision is made. The other action
endpoints in this section act at once.

---

### `POST /api/schedule/add`

Requests to add a **single item** to the schedule.

```bash
# Bible verse — single
curl -k -X POST https://192.168.1.10:8765/api/schedule/add \
  -H "Content-Type: application/json" \
  -d '{"item":{"bookName":"John","chapter":3,"verseNumber":16,"verseText":"For God so loved the world…"}}'

# Bible verse — multi-verse range (Genesis 1:1-3)
curl -k -X POST https://192.168.1.10:8765/api/schedule/add \
  -H "Content-Type: application/json" \
  -d '{"item":{"bookName":"Genesis","chapter":1,"verseNumber":1,"verseText":"In the beginning…","verseRange":"1-3"}}'

# Song
curl -k -X POST https://192.168.1.10:8765/api/schedule/add \
  -H "Content-Type: application/json" \
  -d '{"item":{"songNumber":42,"title":"Great Is Thy Faithfulness","songbook":"Hymns"}}'

# Presentation
curl -k -X POST https://192.168.1.10:8765/api/schedule/add \
  -H "Content-Type: application/json" \
  -d '{"item":{"filePath":"/Users/…/sermon.pptx","fileName":"sermon.pptx","slideCount":5,"fileType":"pptx"}}'

# Media
curl -k -X POST https://192.168.1.10:8765/api/schedule/add \
  -H "Content-Type: application/json" \
  -d '{"item":{"mediaUrl":"/Users/…/worship.mp4","mediaTitle":"Worship Loop","mediaType":"local"}}'
```

**Success** (user clicked Allow):
```json
{ "ok": true }
```

**Denied** (user clicked Deny):
```
HTTP 403
{ "ok": false, "reason": "denied" }
```

**Bad body:**
```
HTTP 400
{ "error": "invalid request body" }
```

---

### `POST /api/schedule/add-batch`

Requests to add **multiple items** in a single call.  
The desktop shows **one permission dialog** covering all items. On Allow, every item is added; on Deny, nothing is added.

```bash
# Multiple Bible verses
curl -k -X POST https://192.168.1.10:8765/api/schedule/add-batch \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      { "bookName": "John", "chapter": 3, "verseNumber": 16, "verseText": "For God so loved the world…" },
      { "bookName": "John", "chapter": 3, "verseNumber": 17, "verseText": "For God sent not his Son to condemn…" },
      { "bookName": "John", "chapter": 3, "verseNumber": 18, "verseText": "He that believeth on him is not condemned…" }
    ]
  }'
```

```swift
// Swift / iOS — add a chapter range
struct BibleVerseItem: Encodable {
    let bookName: String
    let chapter: Int
    let verseNumber: Int
    let verseText: String
}

struct BatchRequest: Encodable {
    let items: [BibleVerseItem]
}

let verses = (16...18).map { v in
    BibleVerseItem(bookName: "John", chapter: 3, verseNumber: v, verseText: "…")
}
var request = URLRequest(url: URL(string: "https://192.168.1.10:8765/api/schedule/add-batch")!)
request.httpMethod = "POST"
request.setValue("application/json", forHTTPHeaderField: "Content-Type")
request.httpBody = try JSONEncoder().encode(BatchRequest(items: verses))
let (data, _) = try await URLSession.shared.data(for: request)
```

```kotlin
// Kotlin / Android
data class BibleVerseItem(
    val bookName: String,
    val chapter: Int,
    val verseNumber: Int,
    val verseText: String
)
data class BatchRequest(val items: List<BibleVerseItem>)

val json = Json.encodeToString(BatchRequest(listOf(
    BibleVerseItem("John", 3, 16, "For God so loved the world…"),
    BibleVerseItem("John", 3, 17, "For God sent not his Son to condemn…")
)))
val response = client.post("https://192.168.1.10:8765/api/schedule/add-batch") {
    contentType(ContentType.Application.Json)
    setBody(json)
}
```

**Success:**
```json
{ "ok": true, "added": 3 }
```

**Denied:**
```
HTTP 403
{ "ok": false, "reason": "denied" }
```

---

### `POST /api/project`

Requests to send an item **directly to projection** (bypasses the schedule, goes live immediately after approval).

Accepts the same body as `/api/schedule/add`.

```bash
# Project a Bible verse immediately
curl -k -X POST https://192.168.1.10:8765/api/project \
  -H "Content-Type: application/json" \
  -d '{"item":{"bookName":"Psalm","chapter":23,"verseNumber":1,"verseText":"The Lord is my shepherd…"}}'

# Project a song
curl -k -X POST https://192.168.1.10:8765/api/project \
  -H "Content-Type: application/json" \
  -d '{"item":{"songNumber":85,"title":"How Great Thou Art","songbook":"Hymns"}}'
```

**Success:**
```json
{ "ok": true }
```

---

### `POST /api/pictures/select`

Selects a picture image by index (triggers display on the presentation output).

```bash
curl -k -X POST https://192.168.1.10:8765/api/pictures/select \
  -H "Content-Type: application/json" \
  -d '{"folder-id":"a1b2c3d4","index":2}'
```

```json
{ "ok": true }
```

---

### `POST /api/songs/{number}/select`

Navigates the live presenter to a specific **section** (0-based index) of the currently projected song. No approval required — takes effect immediately.

`{number}` is the song number (e.g. `42`). The section index maps directly to the `sections` array returned by `GET /api/songs/{identifier}` — `0` is the first verse/chorus, `1` is the second, and so on.

```bash
# Navigate to section 2 via JSON body
curl -k -X POST https://192.168.1.10:8765/api/songs/42/select \
  -H "Content-Type: application/json" \
  -d '{"section":2}'

# Or via query param
curl -k -X POST "https://192.168.1.10:8765/api/songs/42/select?section=2"
```

**Success:**
```json
{ "ok": true }
```

**Bad request** (missing / negative section):
```
HTTP 400
{ "error": "missing or invalid section index" }
```

---

### `POST /api/clear`

Instantly hides the projection display (equivalent to pressing **Clear** in the app). No body or approval required.

```bash
curl -k -X POST https://192.168.1.10:8765/api/clear
```

```json
{ "ok": true }
```

**Optional query param** (dev mode only, like `group=` below) — take down one layer and leave the rest up: `layer=slide` (Bible, songs,
a presentation, a web page, a scene, Q&A or the dictionary), `media` (a video or pictures),
`lowerthird`, `captions`, `announcements`, `props` or `messages`. A layer with nothing on it is left
as it is, and an unknown name clears nothing.

```bash
curl -k -X POST "https://192.168.1.10:8765/api/clear?layer=lowerthird"
```

Over the WebSocket, `clear` takes the same `{"layer": "..."}` in its payload.

**Clear groups** (dev mode only, as is `GET /api/clear-groups`) — `group=` fires one of the operator's clear groups, named by its id or its name
(any case), and takes down every layer in it: e.g. *Clear text* = slide + messages. An unknown group
answers `404 {"ok":false,"reason":"no such clear group"}`. Over the WebSocket, `clear` takes
`{"group": "..."}`, acked with `ok:false` and `no_such_group` for an unknown one.

```bash
curl -k -X POST "https://192.168.1.10:8765/api/clear?group=Clear%20text"
```

`GET /api/clear-groups` lists them, with the layers each clears:

```json
[ { "id": "clear1", "name": "Clear text", "layers": ["SLIDE", "MESSAGES"] } ]
```

---

### `POST /api/take`

**Dev mode only** for now: outside dev mode this answers `403 {"ok":false,"reason":"dev mode only"}` (WebSocket: acked `ok:false`, `dev_mode_only`).

Puts what is cued on Preview on air, as the **Take** button does. Only does anything while
preview mode is on (Settings → System) and something is cued. No body or approval required.
Over the WebSocket, the command is `take`.

```bash
curl -k -X POST https://192.168.1.10:8765/api/take
```

```json
{ "ok": true }
```

---

### `POST /api/message`

**Dev mode only** for now: outside dev mode this answers `403 {"ok":false,"reason":"dev mode only"}` (WebSocket: acked `ok:false`, `dev_mode_only`).

Puts a message up -- a nursery call, say. Every other layer comes down and the message goes up
alone, standing still in the announcement look; it comes down when its duration runs out, on
`POST /api/clear?layer=messages`, or when a song or verse goes live. No approval required.
Over the WebSocket, the command is `message` with the same body as its payload.

**Body** -- your own text, or a message saved in the app's Message panel by name or id, with its
`{tokens}` filled in:

| Field | Type | |
|---|---|---|
| `text` | string | The message itself. Optional when `template` is given. |
| `template` | string | A saved message's name (any case) or id. |
| `tokens` | object | Values for the `{tokens}` in the text, e.g. `{"number": "42"}`. |
| `durationSeconds` | int | How long it stays up; the saved message's when left out, else until cleared. |

```bash
curl -k -X POST https://192.168.1.10:8765/api/message \
  -H "Content-Type: application/json" \
  -d '{"template": "Nursery", "tokens": {"number": "42"}}'
```

```json
{ "ok": true, "text": "Parent of child #42, please come to the nursery" }
```

A body that is not JSON, names a saved message that does not exist, or comes to no text at all is
answered `400` with `{"ok": false, "reason": "..."}`.

---

### `GET /api/props` and `POST /api/props/{id}/on|off|toggle`

**Dev mode only** for now: outside dev mode this answers `403 {"ok":false,"reason":"dev mode only"}`.

Props are the persistent overlays set up in the app's Props panel -- a logo bug, the clock, a
countdown, a badge such as LIVE -- which stay up while songs and verses change under them. Clear
All and a message take them down. No approval required.

`GET /api/props` lists them, with whether each is up:

```json
[ { "id": "prop1", "name": "Logo", "kind": "IMAGE", "on": true },
  { "id": "prop2", "name": "Live", "kind": "BADGE", "on": false } ]
```

`POST /api/props/{id}/on`, `/off` or `/toggle` switches one, named by its id or its name (any
case). An unknown prop is answered `404`, an unknown action `400`.

```bash
curl -k -X POST https://192.168.1.10:8765/api/props/Logo/toggle
```

Over the WebSocket, the command is `prop` with `{"id": "...", "on": true}` -- `false` takes it down,
and leaving `on` out toggles it.

---

### `GET /api/macros` and `POST /api/macro/{name}`

**Dev mode only** for now: outside dev mode this answers `403 {"ok":false,"reason":"dev mode only"}`.

Macros are the named action lists set up in the app's Macros panel -- a message, a lower third, an
OBS scene, a wait, another macro. No approval required.

`GET /api/macros` lists them, with how many actions each holds:

```json
[ { "id": "macro1", "name": "Walk in", "actions": 4 } ]
```

`POST /api/macro/{name}` runs one, named by its id or its name (any case). Running it again while it
is still going starts it over. An unknown macro is answered `404`.

```bash
curl -k -X POST https://192.168.1.10:8765/api/macro/Walk%20in
```

Over the WebSocket, the command is `macro` with `{"name": "Walk in"}`; an unknown macro is acked
with `no_such_macro`.

---

### Lower Thirds (Bitfocus Companion)

One HTTP call runs the entire lower-third sequence — the app cuts the ATEM
key on air (upstream or downstream, per ATEM settings), plays the animation on
its output, waits the animation's exact duration (which only the app knows),
then cuts the key off and clears. The
cuts are invisible because the output is transparent before and after the animation.

Configure the ATEM IP, default M/E + keyer and pre/post-roll margins in
**Settings → ATEM** (pre-configure each keyer's source on the ATEM; the app only
toggles it on air). Without an ATEM configured the endpoints still play the lower
third with correct timing — only the key steps are skipped.

#### `GET /api/lowerthirds`

Lists the lower thirds in the configured folder with their animation durations.

```json
[
  { "name": "Pastor John", "durationMs": 5500 },
  { "name": "Welcome", "durationMs": 3000 }
]
```

#### `POST /api/lowerthirds/{name}/run`

Runs the full timed sequence for the named lower third (file name without
`.json`, case-insensitive). Responds as soon as the sequence starts.

Query parameters (all optional):

| Param | Default | Meaning |
|-------|---------|---------|
| `me` | settings default | 1-based M/E (program output) the upstream keyer is on (ignored for downstream keys) |
| `key` | settings default | 1-based keyer to drive — upstream keyer, or DSK number when downstream; `0` = don't touch any key |
| `keytype` | settings default | `usk` (upstream) or `dsk` (downstream); defaults to the configured ATEM key type |
| `pause` | `false` | pause mid-animation (hold the lower third on screen) |
| `pauseDurationMs` | `2000` | how long to hold when `pause=true` |

```bash
curl -k -X POST "https://192.168.1.10:8765/api/lowerthirds/Pastor%20John/run?me=1&key=1"
```

```json
{ "status": "started", "name": "Pastor John", "durationMs": 5500, "totalMs": 6100, "keyError": null }
```

`totalMs` = pre-roll + duration (+ pause) + post-roll — when the button should
be considered "done". `keyError` is non-null when the ATEM could not be reached;
the lower third still plays.

#### `POST /api/lowerthirds/{name}/show`

Like `run` but stays on air (key on + animation shown) until `hide` is called.
Same `me`/`key` query parameters. `totalMs` is `-1`.

#### `POST /api/lowerthirds/hide`

Ends the running sequence immediately: key off, lower third cleared.

```json
{ "status": "stopped" }
```

#### Bitfocus Companion setup

Use the **Generic HTTP** connection:

1. Add a connection: *Generic: HTTP Requests*, base URL `https://<app-ip>:8765`.
2. Button action: *HTTP POST* with URL `/api/lowerthirds/Pastor%20John/run`.
3. If the API key is enabled in Settings → Server, add header
   `X-Api-Key: <your key>`.

One button per lower third — press it and the whole key + animation sequence
runs with correct timing. Add a second button with `/api/lowerthirds/hide` as a
panic/manual-end key.

There are also standalone key toggles:
`POST /api/atem/key/on` and `POST /api/atem/key/off`
(default to the configured key type and target — upstream M/E + keyer, or the
downstream keyer; override with `?keytype=usk|dsk`, `?me=E`, `?key=M`), which
respond with the real on-air result (`502` on an ATEM error).

> **Tip:** **Settings → Server** lists every lower third with one-click *Go Live
> + Key* / *Go Live* buttons that produce the exact URL (API key
> included when enabled) — paste straight into a Companion HTTP action.

#### Triggering other content from Companion

The same Generic HTTP module can drive the rest of the app — these endpoints
already exist and fire instantly (no approval dialog):

- **Songs** — `POST /api/songs/{number}/select?section=N` shows a song section.
  List songs/sections first with `GET /api/songs` and `GET /api/songs/{identifier}`.
- **Bible** — `POST /api/bible/select` with the verse JSON (fetch it via
  `GET /api/bible?book={id}&chapter={num}`).
- **Pictures / presentations** — `POST /api/pictures/select`,
  `POST /api/presentations/{id}/select`.
- **Clear** — `POST /api/clear` hides the current output.

---

### `POST /api/bible/select`

Instantly displays a Bible verse on the projection output. **No approval dialog** — fires immediately like `select_picture`.

Fetch the verse text first with `GET /api/bible?book={id}&chapter={num}`, then send the verse data here.

```bash
curl -k -X POST https://192.168.1.10:8765/api/bible/select \
  -H "Content-Type: application/json" \
  -d '{"bookName":"John","chapter":3,"verseNumber":16,"verseText":"For God so loved the world…"}'

# Multi-verse range
curl -k -X POST https://192.168.1.10:8765/api/bible/select \
  -H "Content-Type: application/json" \
  -d '{"bookName":"Genesis","chapter":1,"verseNumber":1,"verseText":"In the beginning…","verseRange":"1-3"}'
```

| Field | Type | Required |
|-------|------|----------|
| **`bookName`** | `String` | ✅ |
| **`chapter`** | `Int` | ✅ |
| **`verseNumber`** | `Int` | ✅ |
| `verseText` | `String` | optional — displayed text (fetch from `GET /api/bible?book=…&chapter=…`) |
| `verseRange` | `String` | optional — e.g. `"1-3"` for multi-verse |

**Success:**
```json
{ "ok": true }
```

**Bad body:**
```
HTTP 400
{ "error": "invalid request body" }
```

---

### `POST /api/presentations/{id}/select`

Instantly navigates the live presentation to a specific slide. **No approval dialog.**

`{id}` is the schedule item UUID (from `GET /api/schedule`) or the presentation file hash (from `GET /api/presentations`). Body accepts `index` as JSON **or** as a query param.

```bash
# JSON body
curl -k -X POST https://192.168.1.10:8765/api/presentations/abc123/select \
  -H "Content-Type: application/json" \
  -d '{"index":2}'

# Or via query param
curl -k -X POST "https://192.168.1.10:8765/api/presentations/abc123/select?index=2"
```

**Success:**
```json
{ "ok": true }
```

**Bad request** (missing / negative index):
```
HTTP 400
{ "error": "missing or invalid index" }
```

---

## Strong's Dictionary

All three answer `503 {"error":"dictionary unavailable"}` when the bundled dictionary cannot be read.

### `GET /api/dictionary`

Searches the Strong's dictionary and returns a JSON array of entries.

| Query param | Default | Meaning |
|-------------|---------|---------|
| `q` | `""` | Search text |
| `lang` | `en` | `en` or `ru` (anything else is `en`) |
| `filter` | `all` | `all`, `hebrew` or `greek` |
| `limit` | `100` | Most entries returned |
| `book`, `chapter`, `verse` | — | Canonical KJV numbering (Genesis = 1 … Revelation = 66, as `/api/bible`'s `book-id`): only the Strong's numbers occurring in that reference, narrowing as chapter and verse are added |

### `GET /api/dictionary/{number}`

One entry, e.g. `/api/dictionary/H430`, with optional `?lang=`. `404 {"error":"entry not found"}`
when there is none.

### `GET /api/dictionary/{number}/verses`

The verses a Strong's number appears in, with their text from the loaded Bible — the entry sheet's
"Appears in" list. Optional `limit` (default `25`) and `book`/`chapter`/`verse`, which put the
references in that scope first. `503 {"error":"bible not loaded"}` without a Bible.

```json
{ "number": "H430", "total": 2602,
  "verses": [ { "bookName": "Genesis", "chapter": 1, "verse": 1, "reference": "Genesis 1:1", "text": "In the beginning…" } ] }
```

---

## Bible Files (Instance Link)

These stream raw `.spb` modules so an Instance Link follower can load the primary's Bible through
its own engine. Each answers `404` when there is nothing to send.

| Route | Sends |
|-------|-------|
| `GET /api/bible/file` | The primary Bible module |
| `GET /api/bible/file/secondary` | The secondary Bible module |
| `GET /api/bible/file/translations` | JSON array of the ordered module file names |
| `GET /api/bible/file/translation/{index}` | The module at that position (from 0) in that list |

---

## Backgrounds

### `GET /api/backgrounds`

The desktop's current background settings as JSON. Image and video fields are file paths on the
desktop; fetch the bytes through the asset route below.

### `GET /api/backgrounds/asset/{slot}`

The background image — or with `?type=video` the video — configured for one slot. `{slot}` is one of
`default`, `defaultLowerThird`, `bible`, `bibleLowerThird`, `song`, `songLowerThird`. Range requests
are supported. `404` when the slot has nothing configured or the file is missing.

---

## Media

### `GET /api/media/stream/{id}`

Streams the local media file behind schedule item `{id}` (a `media` item whose type is `local`), so a
follower can play it without a copy. Range requests are supported, so seeking works. `404` for an
unknown item or a missing file.

### `POST /api/media/upload?name=clip.mp4`

Uploads a video or audio file as the **raw request body** (`application/octet-stream`), streamed to
disk (`~/.churchpresenter/device_media/`). `name` is required. Accepted extensions are those the
desktop player plays (video: `mp4`, `mov`, `avi`, `mkv`, `wmv`, `flv`, `webm`, `m4v`; audio: `mp3`,
`wav`, `flac`, `aac`, `ogg`, `wma`, `m4a`, `aiff`, `opus`).

```bash
curl -X POST --data-binary @clip.mp4 -H "Content-Type: application/octet-stream" \
  "http://192.168.1.10:8765/api/media/upload?name=clip.mp4"
```

```json
{ "ok": true, "path": "/Users/…/device_media/clip.mp4", "name": "clip", "mediaType": "local" }
```

`mediaType` is `local` for video and `audio` for audio; add the returned `path` to the schedule as a
media item. Errors: `403` when file upload is disabled, `400` without `name`, `413` over the size
limit (`permissions.maxMediaUploadMb` in `/api/status`), `415` for another file type.

---

## Uploads

Both take a JSON body with the file as a base64 **data URI**, and answer `403 {"error":"file upload
is disabled"}` when uploads are switched off in Settings → Server.

### `POST /api/presentations/upload`

```json
{ "name": "slides.pdf", "data": "data:application/pdf;base64,JVBERi0x…" }
```

Accepts `pdf`, `ppt`, `pptx` and `key`, up to 200 MB. The desktop opens it in the Presentations tab;
only the latest device upload is kept. Answers `{"ok":true,"id":"<hex>","name":"slides"}`.

### `POST /api/pictures/upload`

```json
{ "name": "photo.jpg", "data": "data:image/jpeg;base64,/9j/4AAQ…" }
```

Saves the image into that day's *Device Photos* folder and broadcasts `pictures_updated` with it.
Answers `{"ok":true,"folder-id":"…","image-index":3,"file-name":"photo.jpg"}`; the folder is then
served by `/api/pictures/{id}` and can be shown with `POST /api/pictures/select`.

---

## Calendar Enrollment

### `POST /api/calendar/enroll`

A phone asking to plan the desktop's calendar through the calendar relay. Requires `X-Device-Id`.

```json
{ "deviceName": "Pastor's iPhone", "code": "482913" }
```

`code` is the six-digit code the phone is showing; the operator compares it with the one in the
desktop's prompt and allows the device. The request waits up to two minutes for that answer.

| Status | Body |
|--------|------|
| `200` | `{"relayUrl":"…","instanceId":"…","deviceId":"…","deviceToken":"…","instanceKey":"…"}` |
| `400` | `device id required` / `code required` |
| `403` | `{"error":"enrollment denied"}` |
| `408` | `{"error":"enrollment timed out"}` |
| `409` | `{"error":"sync_off"}` — calendar sync is off on the desktop |
| `429` | `{"error":"enrollment already pending"}` — one open request per device, five in all |
| `502` | `{"error":"relay unreachable"}` |

---

## Presentation Remote

A phone-in-hand clicker for the presentation on the desktop, served as its own web page at
`GET /presentation-remote`. It must be enabled in the desktop's presentation settings; the password,
when set, goes in `X-Presentation-Password` or `?password=` on every call below except the page and
`status`.

### `GET /api/presentation-remote/status`

Open, so the page can show its state before signing in:

```json
{ "enabled": true, "id": "3f2a1b4c", "index": 2, "total": 12, "frozen": false,
  "isPlaying": false, "isLive": true, "autoScrollInterval": 0, "looping": false,
  "passwordRequired": true, "notes": "Speaker notes for this slide" }
```

### Commands

Each answers `{"ok":true}`.

| Route | Does |
|-------|------|
| `POST /api/presentation-remote/auth` | Checks the password, then asks the operator to approve this device (once) — `403 {"error":"connection denied"}` if refused |
| `POST /api/presentation-remote/next` | Next slide |
| `POST /api/presentation-remote/previous` | Previous slide |
| `POST /api/presentation-remote/goto/{index}` | Slide `{index}` (from 0, clamped to the deck) |
| `POST /api/presentation-remote/freeze` | Toggles blank / unblank |
| `POST /api/presentation-remote/play-pause` | Toggles auto-advance |
| `POST /api/presentation-remote/loop` | Toggles looping |
| `POST /api/presentation-remote/go-live` | Sends the presentation to the output |
| `POST /api/presentation-remote/upload` | Uploads a deck, same body and answer as `POST /api/presentations/upload` |

The remote follows the desktop through the `presentation_*` WebSocket events.

---

## Q&A

The audience submits questions from a public page; a moderator approves, edits and displays them
from an admin page. Pages: `GET /qa` (submit), `GET /qa/vote` (vote) and `GET /qa/admin` (moderate).

### Public

| Route | Body | Answers |
|-------|------|---------|
| `GET /api/qa/status` | — | `{"sessionActive":true,"cooldownSeconds":30,"displayedQuestionId":"…","votingEnabled":false}` |
| `POST /api/qa/submit` | `{"text":"…","name":"optional"}` | The new question (`QuestionDto`); `403` when no session is active, `429` within the cooldown |
| `GET /api/qa/approved` | — | `[{"id":"…","text":"…","voteCount":3,"voted":"up"}]` (`voted` is this client's vote or `null`); `403` when voting is off |
| `POST /api/qa/vote` | `{"questionId":"…","direction":"up"}` (`up` or `down`) | `{"ok":true,"voted":"up"}`; voting again the same way takes the vote back |

Submissions and votes are keyed by client IP (`CF-Connecting-IP`, then `X-Forwarded-For`, then the
socket's address). `X-Device-Id` is recorded with a submission when sent.

### Moderation

Every route here takes the admin password (`X-QA-Password` or `?password=`). All but
`questions` and `clear-all` also wait for the operator to allow the action on the desktop, answering
`403 {"error":"denied by operator"}` if refused. `{id}` is the question's id.

| Route | Body | Does |
|-------|------|------|
| `POST /api/qa/auth` | — | Checks the password and asks the operator to approve this device (once) |
| `GET /api/qa/questions` | `?status=pending\|approved\|denied\|done` (optional) | All questions, as `QuestionDto`s |
| `POST /api/qa/questions/{id}/approve` | — | Approves it |
| `POST /api/qa/questions/{id}/edit` | `{"text":"…"}` | Changes its text |
| `POST /api/qa/questions/{id}/deny` | — | Denies it |
| `POST /api/qa/questions/{id}/done` | — | Marks it answered |
| `POST /api/qa/questions/{id}/display` | — | Puts it on the output (it must be approved) |
| `DELETE /api/qa/questions/{id}` | — | Deletes it |
| `POST /api/qa/add` | `{"text":"…"}` | Adds a question as the moderator; answers the `QuestionDto` |
| `POST /api/qa/clear-display` | — | Takes the displayed question down |
| `POST /api/qa/clear-all` | — | Deletes every question, **without** asking the operator |

`QuestionDto`: `id`, `text`, `submitterName`, `submitterDeviceId`, `timestamp`, `status`,
`voteCount`, `upvotes`, …. Changes are pushed as `questions_updated` over the WebSocket.

---

## Lower Third JSON and ATEM Media Pool

### `GET /api/lowerthirds/{name}/json`

The raw Lottie JSON of a lower-third preset (name without `.json`, any case), so an Instance Link
follower plays the same animation. `404` when there is no such preset.

### `POST /api/atem/still/{name}` and `POST /api/atem/clip/{name}`

Renders the named lower third and uploads it to the ATEM's media pool — one still frame, or the full
animation as a clip. Both answer at once and upload in the background:

```json
{ "status": "uploading", "type": "clip", "name": "Pastor John", "slot": 1, "me": 1, "key": 1 }
```

| Query param | Meaning |
|-------------|---------|
| `slot` | 1-based still or clip slot; defaults to the slot in Settings → ATEM |
| `key` | When above 0, puts this keyer on air after the upload (a clip's key goes off again after the clip) |
| `me` | 1-based M/E for an upstream key |
| `keytype` | `usk` or `dsk`; defaults to the configured key type |

Errors: `404` for an unknown lower third, `503 {"error":"ATEM not configured"}`, `400` for a key the
switcher does not have, and for a clip `422` when it has more frames than the slot holds.

---

## Browser Source (OBS / vMix)

Each output set to *Browser Source* in Projection Settings is served as a web page that OBS, vMix or
any browser can load. `{index}` is the output's number as Projection Settings shows it, from 1. An
unknown or disabled output answers `404`. When the output has *Require API key* on, add
`?apiKey=<key>` to the URL (or `X-Api-Key`).

### `GET /browser-source/{index}`

The overlay page (`Cache-Control: no-store`). Optional `?bg=` overrides the page background.

### `WS /api/browser-source/{index}/ws`

The page's frame stream, one way, server to page. Each **binary** message is a 24-byte big-endian
header — `x`, `y`, `rectWidth`, `rectHeight`, `fullWidth`, `fullHeight` as six Int32s — followed by the
changed rectangle as PNG (first byte `0x89`, when it has transparency) or JPEG (`0xFF`). A frame is
sent only when pixels change, and the last one is re-sent every 15 seconds to keep the connection
alive. When the output's profile offers transpose buttons the page also receives **text** messages
with the current transpose state, and one with `controls:false` when it stops offering them. A bad
request closes the socket with a reason (`CANNOT_ACCEPT`, `VIOLATED_POLICY` for the key,
`TRY_AGAIN_LATER` while the renderer starts).

### `POST /api/browser-source/{index}/auth` and `POST /api/browser-source/{index}/transpose`

The musicians' transpose buttons on a tablet showing the page. Only an output whose profile offers
them accepts either (`403` otherwise). `auth` asks the operator to approve the device (send
`X-Device-Id`) and answers `{"ok":true,"output":1}`. `transpose` takes `{"delta":1}`, `{"delta":-1}`
or `{"reset":true}` from an approved device (`403 {"error":"device not approved"}` otherwise).

---

## CA Certificate

`GET /ca.crt` (DER, `application/x-x509-ca-cert`) and `GET /ca.pem` (PEM) download the CA certificate
of the server's own certificate authority, for a device to install before talking HTTPS to it. Both
are open — a device needs them before it can make any other call — and answer `404` while the server
runs plain HTTP.

---

## WebSocket

### Connection

```
ws://192.168.1.10:8765/ws
```

With API key:

```
ws://192.168.1.10:8765/ws?apiKey=mysecretkey
```

`GET /ws` upgrades to the WebSocket (the route index lists it as `WS /ws`). Identify the device
with the `X-Device-Id` header (or an `X-Device-Id=` query param): a device the operator has blocked
receives `{"error":"Blocked"}` and nothing more, and a device blocked mid-session has every command
refused with reason `blocked`. A wrong API key receives `{"error":"Unauthorized"}`. Another
ChurchPresenter following this one over Instance Link also sends `X-Client-Role: instance_link`.

On connect the server immediately pushes the current state as a burst, in this order:

| Sent on connect | Condition |
|-----------------|-----------|
| `songs_updated` | Always |
| `bible_updated` | If a Bible translation is loaded |
| `schedule_updated` | Always |
| `presentation_updated` | If a presentation is currently loaded in the Presentations tab |
| `pictures_updated` | If a picture folder is currently loaded in the Pictures tab |
| `backgrounds_updated` | Always (empty payload — an invalidation signal) |
| `secondary_bible_updated` | Always (empty payload — an invalidation signal) |
| `presentation_slide_changed` | Always — the current presentation position |
| `live_state_changed` | If anything has gone live since the server started |

Broadcasts that happen while the burst is being written are held and delivered after it.

```javascript
// JavaScript / React Native
const ws = new WebSocket('ws://192.168.1.10:8765/ws');

ws.onopen = () => console.log('connected');

ws.onmessage = (e) => {
  const msg = JSON.parse(e.data);          // { type, payload }
  const data = JSON.parse(msg.payload);    // payload is also JSON-encoded
  switch (msg.type) {
    case 'songs_updated':    handleSongs(data);    break;
    case 'bible_updated':    handleBible(data);    break;
    case 'schedule_updated': handleSchedule(data); break;
  }
};
```

All messages (both directions) share the same envelope:

```json
{ "type": "event_or_command_name", "payload": "<json-encoded-string>", "commandId": "optional" }
```

> `payload` is a **JSON-encoded string** (not an object) — double-decode it. A few events carry a
> bare value or an empty string instead; the table below says which.

**Command acks.** A command sent with a `commandId` is answered with a `command_ack` event whose
payload is `{"commandId":"…","ok":true,"reason":null}`. Without a `commandId` there is no ack.
Reasons sent with `ok:false`: `unknown_command`, `invalid_payload`, `dev_mode_only`, `blocked`,
`no_such_group`, `no_message`, `no_such_prop`, `no_such_macro`. Approval-gated commands ack at once
with `ok:true` and reason `pending_approval`; the operator's decision then arrives as a bare
`{"ok":true}` or `{"ok":false,"reason":"denied"}` text frame (not an envelope), and a granted change
also as `schedule_updated`.

---

### Server → Client Events

| `type` | `payload` | When fired |
|--------|-----------|------------|
| `songs_updated` | `SongCatalogResponse` (as `GET /api/songs`) | On connect + whenever songs are reloaded |
| `bible_updated` | `BibleCatalogResponse` (as `GET /api/bible`) | On connect (if loaded) + whenever the Bible is reloaded |
| `secondary_bible_updated` | empty | On connect + whenever the secondary Bible or the ordered translation list changes — refetch `GET /api/bible/file/secondary` or `GET /api/bible/file/translations` |
| `backgrounds_updated` | empty | On connect + whenever the background settings change — refetch `GET /api/backgrounds` |
| `schedule_updated` | `ScheduleResponse` (as `GET /api/schedule`) | On connect + every schedule change |
| `presentation_updated` | `PresentationCatalogResponse` (as `GET /api/presentations`) | On connect (if loaded) + when a presentation is loaded |
| `pictures_updated` | `PictureFolderResponse` (as `GET /api/pictures/{id}`) | On connect (if loaded) + when a picture folder is opened or a picture is uploaded |
| `display_cleared` | empty | The output was cleared |
| `song_section_selected` | the section index, as a bare number (e.g. `2`) | A song section went live |
| `questions_updated` | empty | Any Q&A change — refetch the questions |
| `presentation_slide_changed` | `{"id","index","total","isPlaying","isLive","notes"}` (`notes` absent on connect and when cleared) | On connect + every slide change |
| `presentation_freeze_changed` | `{"frozen":true}` | The presentation was blanked or unblanked |
| `presentation_live_changed` | `{"isLive":true}` | The presentation went on or off air |
| `presentation_auto_scroll_changed` | `{"autoScrollInterval":5}` (seconds) | The auto-advance interval changed |
| `presentation_looping_changed` | `{"looping":true}` | Looping was switched |
| `live_state_changed` | `LiveStateDto`: `contentType` plus the fields of what is live (verse, song section, picture, media, announcement, website, scene, Q&A, dictionary, lower third), `liveSlide`, `overlays`, `message`, `messageDurationSeconds`, `props` | On connect (if any) + whenever what is on air changes |
| `media_state_changed` | `{"isLive","isLoaded","isPlaying","title","positionMs","durationMs","volume","muted","mediaType","source"}` | Sent by the desktop on a fixed cadence, so the position ticks |
| `command_ack` | `{"commandId","ok","reason"}` | In answer to a command that carried a `commandId` |

```javascript
// Handle schedule updates
ws.onmessage = (e) => {
  const { type, payload } = JSON.parse(e.data);
  if (type === 'schedule_updated') {
    const { items, total } = JSON.parse(payload);
    renderSchedule(items);
  }
};
```

---

### Client → Server Commands

Send a command with `ws.send(JSON.stringify({ type, payload }))`.  
`payload` must be **JSON-encoded as a string**.

| `type` | Payload | Action | Approval needed |
|--------|---------|--------|----------------|
| `select_song` | `{id, songNumber, title, songbook}` | Navigate schedule to a song | No |
| `select_song_section` | `{number, section}` | Jump to a section within current song | No |
| `select_slide` | `{id, index}` | Jump to a slide in current presentation | No |
| `select_bible_verse` | as `POST /api/bible/select` | Display a Bible verse immediately | No |
| `select_picture` | `{"folder-id", index, "file-name"?}` | Select an image in a picture folder | No |
| `bible_hold` | `{"hold": true}` (default `true`) | Holds or releases the Bible output | No |
| `next_picture` | — | Next picture of whatever is live | No |
| `previous_picture` | — | Previous picture of whatever is live | No |
| `next_slide` | — | Next slide of whatever is live | No |
| `previous_slide` | — | Previous slide of whatever is live | No |
| `media_play_pause` | — | Toggles media playback | No |
| `media_stop` | — | Stops media | No |
| `media_seek_forward` | — | Seeks media forward | No |
| `media_seek_backward` | — | Seeks media back | No |
| `media_seek_to` | the position in ms, as a bare number (`"90000"`) | Seeks media to that position | No |
| `media_set_volume` | the volume `0.0`–`1.0`, as a bare number | Sets the media volume | No |
| `media_mute_toggle` | — | Mutes or unmutes media | No |
| `clear` | empty, `{"layer": "..."}` or `{"group": "..."}` | Clear / hide the projection display; a layer or a clear group is **dev mode only** | No |
| `take` | — | Take, as `POST /api/take` — **dev mode only** | No |
| `message` | as `POST /api/message` | Puts a message up — **dev mode only** | No |
| `prop` | `{"id": "...", "on": true}` (`on` absent toggles) | Switches a prop — **dev mode only** | No |
| `macro` | `{"name": "..."}` | Runs a macro — **dev mode only** | No |
| `add_to_schedule` | `{item: {...}}` | Add a single item to the schedule | ✅ Yes |
| `add_batch_to_schedule` | `{items: [...]}` | Add multiple items to the schedule | ✅ Yes |
| `remove_from_schedule` | `{"id": "<schedule item id>"}` | Remove an item from the schedule | ✅ Yes |
| `project` | `{item: {...}}` | Project an item immediately | ✅ Yes |

The `next_*`, `previous_*` and `media_*` commands without a payload act on whatever the desktop has
live, so a controller does not need the desktop's ids. An unknown `type` is acked `unknown_command`.

---

#### `select_song`

```javascript
ws.send(JSON.stringify({
  type: 'select_song',
  payload: JSON.stringify({
    id: '',
    songNumber: 42,
    title: 'Great Is Thy Faithfulness',
    songbook: 'Hymns'
  })
}));
```

---

#### `add_to_schedule`

Add a single item (triggers the desktop permission dialog).

```javascript
// Bible verse — single
ws.send(JSON.stringify({
  type: 'add_to_schedule',
  payload: JSON.stringify({
    item: {
      bookName: 'John',
      chapter: 3,
      verseNumber: 16,
      verseText: 'For God so loved the world…'
    }
  })
}));

// Bible verse — multi-verse range (Genesis 1:1-3)
ws.send(JSON.stringify({
  type: 'add_to_schedule',
  payload: JSON.stringify({
    item: {
      bookName: 'Genesis',
      chapter: 1,
      verseNumber: 1,
      verseText: 'In the beginning…',
      verseRange: '1-3'
    }
  })
}));

// Song
ws.send(JSON.stringify({
  type: 'add_to_schedule',
  payload: JSON.stringify({
    item: { songNumber: 42, title: 'Great Is Thy Faithfulness', songbook: 'Hymns' }
  })
}));
```

---

#### `add_batch_to_schedule`

Add multiple items at once (one permission dialog on the desktop).

```javascript
ws.send(JSON.stringify({
  type: 'add_batch_to_schedule',
  payload: JSON.stringify({
    items: [
      { bookName: 'John', chapter: 3, verseNumber: 16, verseText: 'For God so loved…' },
      { bookName: 'John', chapter: 3, verseNumber: 17, verseText: 'For God sent not his Son…' },
      { bookName: 'John', chapter: 3, verseNumber: 18, verseText: 'He that believeth…' }
    ]
  })
}));
```

---

#### `project`

Send an item directly to projection.

```javascript
ws.send(JSON.stringify({
  type: 'project',
  payload: JSON.stringify({
    item: {
      bookName: 'Psalm',
      chapter: 23,
      verseNumber: 1,
      verseText: 'The Lord is my shepherd…'
    }
  })
}));
```

---

#### `select_picture`

```javascript
ws.send(JSON.stringify({
  type: 'select_picture',
  payload: JSON.stringify({ 'folder-id': 'a1b2c3d4', index: 2 })
}));
```

---

#### `select_song_section`

Navigates the live presenter to a specific section (0-based) of the currently projected song. No approval required — takes effect immediately.

`number` is the song number as a string. `section` is the 0-based section index matching the `sections` array from `GET /api/songs/{identifier}`.

```javascript
ws.send(JSON.stringify({
  type: 'select_song_section',
  payload: JSON.stringify({ number: '42', section: 2 })
}));
```

---

#### `select_slide`

Instantly navigates the live presentation to a specific slide. No approval required.

`id` is the presentation file hash or schedule item UUID. `index` is the 0-based slide index.

```javascript
ws.send(JSON.stringify({
  type: 'select_slide',
  payload: JSON.stringify({ id: 'abc123', index: 2 })
}));
```

---

#### `select_bible_verse`

Instantly displays a Bible verse on the projection output. No approval required.

```javascript
// Single verse
ws.send(JSON.stringify({
  type: 'select_bible_verse',
  payload: JSON.stringify({
    bookName: 'John',
    chapter: 3,
    verseNumber: 16,
    verseText: 'For God so loved the world…'
  })
}));

// Multi-verse range
ws.send(JSON.stringify({
  type: 'select_bible_verse',
  payload: JSON.stringify({
    bookName: 'Genesis',
    chapter: 1,
    verseNumber: 1,
    verseText: 'In the beginning…',
    verseRange: '1-3'
  })
}));
```

---

#### `clear`

Instantly hides the projection display.

```javascript
ws.send(JSON.stringify({
  type: 'clear',
  payload: ''
}));
```

---

## Item Type Reference

The server auto-detects item type from which fields are present. Required fields are **bold**.

### Song
| Field | Type | Required |
|-------|------|----------|
| **`songNumber`** | `Int` | ✅ |
| `title` | `String` | recommended |
| `songbook` | `String` | recommended |
| `id` | `String` | optional (auto-generated) |

### Bible Verse
| Field | Type | Required |
|-------|------|----------|
| **`bookName`** | `String` | ✅ |
| **`chapter`** | `Int` | ✅ |
| **`verseNumber`** | `Int` | ✅ (first verse in the range) |
| `verseText` | `String` | optional (defaults to `""`) |
| `verseRange` | `String` | optional — e.g. `"1-3"` or `"2,4"` for multi-verse items; omit for a single verse |
| `id` | `String` | optional (auto-generated) |

### Presentation
| Field | Type | Required |
|-------|------|----------|
| **`filePath`** | `String` | ✅ |
| `fileName` | `String` | recommended |
| `slideCount` | `Int` | recommended |
| `fileType` | `String` | `"pptx"` / `"key"` / `"pdf"` |

### Picture Folder
| Field | Type | Required |
|-------|------|----------|
| **`folderPath`** | `String` | ✅ |
| `folderName` | `String` | recommended |
| `imageCount` | `Int` | recommended |

### Media
| Field | Type | Required |
|-------|------|----------|
| **`mediaUrl`** | `String` | ✅ |
| `mediaTitle` | `String` | recommended |
| `mediaType` | `String` | `"local"` / `"stream"` |

---

> **Schedule-only types** — The following types appear in `GET /api/schedule` responses but **cannot** be added remotely via `POST /api/schedule/add` or `POST /api/project`.

### Label
| Field | Type | Notes |
|-------|------|-------|
| `text` | `String` | Label text content |
| `textColor` | `String` | CSS-style hex colour, e.g. `"#ffffff"` |
| `backgroundColor` | `String` | CSS-style hex colour, e.g. `"#000000"` |

### Lower Third
| Field | Type | Notes |
|-------|------|-------|
| `presetId` | `String` | Preset identifier |
| `presetLabel` | `String` | Display name of the preset |

### Announcement
| Field | Type | Notes |
|-------|------|-------|
| `text` | `String` | Announcement text content |
| `textColor` | `String` | CSS-style hex colour, e.g. `"#ffffff"` |
| `backgroundColor` | `String` | CSS-style hex colour, e.g. `"#000000"` |

### Website
| Field | Type | Notes |
|-------|------|-------|
| `url` | `String` | URL to display in the presenter |
| `title` | `String` | Display title for the item |

---

---

## Mobile Workflows

Complete step-by-step flows for the most common mobile use cases.

---

### Display a Bible Verse

**Goal:** browse the Bible on mobile, pick a verse, and show it live on the projection screen.

```
1. GET  /api/bible                          → get all books with chapter counts
2. GET  /api/bible?book=43&chapter=3        → get all verses in John 3 with text
3. POST /api/bible/select                   → display verse immediately (no approval)
   body: { bookName, chapter, verseNumber, verseText, verseRange? }
```

```javascript
// 1. Load chapter
const chapResp = await fetch('https://host:8765/api/bible?book=43&chapter=3');
const chapter = await chapResp.json();
// chapter.verses = [ { verse: 1, text: "…" }, … ]

// 2. User picks verse 16 — display it instantly
const verse = chapter.verses.find(v => v.verse === 16);
await fetch('https://host:8765/api/bible/select', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    bookName: chapter['book-name'],
    chapter: chapter.chapter,
    verseNumber: verse.verse,
    verseText: verse.text
  })
});
```

> To **add to schedule** instead of displaying instantly, use `POST /api/schedule/add` (requires desktop approval). To display with approval, use `POST /api/project`.

---

### Pick a Picture to Show

**Goal:** browse a picture folder loaded in the schedule and show a specific image live.

```
1. GET  /api/schedule                                → find picture items, note their id
2. GET  /api/pictures/{id}                           → get image catalog for that folder
3. GET  /api/pictures/{id}/images/{index}            → fetch thumbnail to preview on mobile
4. POST /api/pictures/select                         → show image on screen (no approval)
   body: { "folder-id": "{id}", "index": N }
```

```javascript
// 1. Get schedule, find a picture item
const schedResp = await fetch('https://host:8765/api/schedule');
const { items } = await schedResp.json();
const picItem = items.find(i => i.type === 'picture');

// 2. Load the folder catalog
const catResp = await fetch(`https://host:8765/api/pictures/${picItem.id}`);
const catalog = await catResp.json();
// catalog.images = [ { index: 0, "file-name": "…", "thumbnail-url": "…" }, … ]

// 3. User browses thumbnails: fetch each via catalog.images[n]["thumbnail-url"]

// 4. User picks index 3 — show it live
await fetch('https://host:8765/api/pictures/select', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ 'folder-id': picItem.id, index: 3 })
});
```

> Use the WS command `select_picture` for the same effect over an open WebSocket connection.

---

### Navigate Presentation Slides

**Goal:** browse a loaded presentation on mobile and switch to a specific slide live.

```
1. GET  /api/schedule                                     → find presentation items, note id
2. GET  /api/presentations/{id}                           → get slide list with thumbnail URLs
3. GET  /api/presentations/{id}/slides/{index}            → fetch JPEG thumbnail for mobile preview
4. POST /api/presentations/{id}/select                    → show slide on screen (no approval)
   body: { "index": N }
```

```javascript
// 1. Find presentation in schedule
const schedResp = await fetch('https://host:8765/api/schedule');
const { items } = await schedResp.json();
const presItem = items.find(i => i.type === 'presentation');

// 2. Load slide catalog
const presResp = await fetch(`https://host:8765/api/presentations/${presItem.id}`);
const pres = await presResp.json();
// pres.slides = [ { "slide-index": 0, "thumbnail-url": "/api/presentations/…/slides/0" }, … ]

// 3. User browses slides: each thumbnail-url returns a JPEG
//    e.g. fetch(`https://host:8765${pres.slides[0]["thumbnail-url"]}`)

// 4. User picks slide 2 — switch live
await fetch(`https://host:8765/api/presentations/${presItem.id}/select`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ index: 2 })
});
```

> Slides are rendered in the background when the schedule loads. If `GET /api/presentations/{id}` returns `404` in the first few seconds after `schedule_updated`, retry after a short delay.  
> Use the WS command `select_slide` for the same effect over an open WebSocket connection.

---

### Instant vs. Approval-Required Actions

| Action | Instant (no dialog) | With approval dialog |
|--------|--------------------|--------------------|
| Show a Bible verse | `POST /api/bible/select` · WS `select_bible_verse` | `POST /api/project` |
| Show a picture | `POST /api/pictures/select` · WS `select_picture` | `POST /api/project` |
| Navigate to a slide | `POST /api/presentations/{id}/select` · WS `select_slide` | — |
| Navigate to a song section | `POST /api/songs/{number}/select` · WS `select_song_section` | — |
| Add Bible verse to schedule | — | `POST /api/schedule/add` |
| Add song to schedule | — | `POST /api/schedule/add` |
| Clear projection | `POST /api/clear` · WS `clear` | — |

---

## Error Reference

| HTTP status | Meaning |
|-------------|---------|
| `200 OK` | Request succeeded |
| `400 Bad Request` | Body could not be parsed or required fields missing |
| `401 Unauthorized` | API key is wrong or missing (when auth is enabled) |
| `401 Unauthorized` (presentation remote / Q&A) | Wrong presentation or Q&A admin password |
| `403 Forbidden` | Desktop user clicked **Deny**; a dev-mode-only route outside dev mode; file upload, the presentation remote or Q&A voting switched off |
| `404 Not Found` | Resource (song, slide, image, presentation) does not exist |
| `408 Request Timeout` | Calendar enrollment not answered within two minutes |
| `409 Conflict` | Calendar enrollment while calendar sync is off |
| `413 Payload Too Large` | Upload over the size limit |
| `415 Unsupported Media Type` | Upload of a file type the desktop does not take |
| `422 Unprocessable Entity` | ATEM clip longer than its slot holds |
| `429 Too Many Requests` | Q&A submission inside the cooldown; a calendar enrollment already pending |
| `500 Internal Server Error` | Unexpected server-side error, or an upload that could not be written |
| `502 Bad Gateway` | ATEM key toggle failed; calendar relay unreachable |
| `503 Service Unavailable` | Data not yet loaded (e.g. Bible not loaded, no picture folder open, ATEM not configured, dictionary unavailable) |
