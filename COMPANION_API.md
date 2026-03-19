# ChurchPresenter Companion API

REST + WebSocket API exposed by the desktop app for the mobile companion.

---

## Base URL

| Protocol | Address | Notes |
|----------|---------|-------|
| HTTPS | `https://<desktop-ip>:<port>` | Default port **8765** — used by external devices |
| HTTP  | `http://127.0.0.1:<port+1>` | e.g. `8766` — localhost only (embedded WebView) |

The server displays its full URL in **Settings → Server**.  
Replace `https://192.168.1.10:8765` with the URL shown there in every sample below.

> **Self-signed certificate** — mobile apps must accept the cert or pin it on first connect.

---

## Authentication

Authentication is **optional** and disabled by default.  
When enabled, pass the API key on **every** request.

| Method | Where |
|--------|-------|
| HTTP header | `X-Api-Key: <key>` |
| Query param | `?apiKey=<key>` |

If the key is wrong the server responds `HTTP 401 Unauthorized`.

```bash
# Header
curl -k -H "X-Api-Key: mysecretkey" https://192.168.1.10:8765/api/info

# Query param
curl -k "https://192.168.1.10:8765/api/info?apiKey=mysecretkey"
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

### `GET /api/songs/{number}`

Returns full song detail including all lyric sections.

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

All action endpoints **suspend** until the desktop user clicks **Allow** or **Deny** in the permission dialog.  
The HTTP connection stays open until that decision is made.

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

`{number}` is the song number (e.g. `42`). The section index maps directly to the `sections` array returned by `GET /api/songs/{number}` — `0` is the first verse/chorus, `1` is the second, and so on.

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

## WebSocket

### Connection

```
wss://192.168.1.10:8765/ws
```

With API key:

```
wss://192.168.1.10:8765/ws?apiKey=mysecretkey
```

On connect the server immediately pushes the current state as a burst of up to **5 events**:

| Sent on connect | Condition |
|-----------------|-----------|
| `songs_updated` | Always |
| `bible_updated` | If a Bible translation is loaded |
| `schedule_updated` | Always |
| `presentation_updated` | If a presentation is currently loaded in the Presentations tab |
| `pictures_updated` | If a picture folder is currently loaded in the Pictures tab |

```javascript
// JavaScript / React Native
const ws = new WebSocket('wss://192.168.1.10:8765/ws');

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
{ "type": "event_or_command_name", "payload": "<json-encoded-string>" }
```

> `payload` is a **JSON-encoded string** (not an object) — double-decode it.

---

### Server → Client Events

| `type` | `payload` decoded type | When fired |
|--------|------------------------|------------|
| `songs_updated` | `SongCatalogResponse` | On connect + whenever songs are reloaded |
| `bible_updated` | `BibleCatalogResponse` | On connect (if loaded) + whenever Bible is reloaded |
| `schedule_updated` | `ScheduleResponse` | On connect + every schedule change |
| `presentation_updated` | `PresentationCatalogResponse` | On connect (if loaded) + when a presentation is loaded |
| `pictures_updated` | `PictureFolderResponse` | On connect (if loaded) + when a picture folder is opened |

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

| `type` | Action | Approval needed |
|--------|--------|----------------|
| `select_song` | Navigate schedule to a song | No |
| `select_song_section` | Jump to a section within current song | No |
| `select_slide` | Jump to a slide in current presentation | No |
| `select_bible_verse` | Display a Bible verse immediately | No |
| `select_picture` | Select an image in the current picture folder | No |
| `clear` | Clear / hide the projection display | No |
| `add_to_schedule` | Add a single item to the schedule | ✅ Yes |
| `add_batch_to_schedule` | Add multiple items to the schedule | ✅ Yes |
| `project` | Project an item immediately | ✅ Yes |

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

`number` is the song number as a string. `section` is the 0-based section index matching the `sections` array from `GET /api/songs/{number}`.

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

## Lottie Generator (Lower Third Editor)

The server also hosts an embedded **browser-based Lower Third / Lottie Generator** editor. These endpoints are used by the generator page itself and are available to custom tooling.

### `GET /lottie-generator.html`

Serves the generator HTML page. Open this URL in a browser to use the editor.

```bash
open https://192.168.1.10:8765/lottie-generator.html
```

---

### `GET /api/presets`

Returns saved generator presets as a JSON array.

```bash
curl -k https://192.168.1.10:8765/api/presets
```

```json
[ { "name": "Announcement", "color": "#ffffff" }, … ]
```

---

### `POST /api/presets`

Saves (overwrites) the full presets array. Body is a raw JSON array.

```bash
curl -k -X POST https://192.168.1.10:8765/api/presets \
  -H "Content-Type: application/json" \
  -d '[{ "name": "Announcement", "color": "#ffffff" }]'
```

```json
{ "ok": true }
```

---

### `GET /api/color-themes`

Returns saved color themes as a JSON array. Falls back to bundled defaults if no custom themes have been saved.

```bash
curl -k https://192.168.1.10:8765/api/color-themes
```

```json
[ { "name": "Dark", "primary": "#000000", "text": "#ffffff" }, … ]
```

---

### `POST /api/color-themes`

Saves (overwrites) the full color themes array.

```bash
curl -k -X POST https://192.168.1.10:8765/api/color-themes \
  -H "Content-Type: application/json" \
  -d '[{ "name": "Dark", "primary": "#000000", "text": "#ffffff" }]'
```

```json
{ "ok": true }
```

---

### `GET /api/logos`

Returns an array of available logo filenames (merged from the user-configured lower-third folder and bundled assets).

```bash
curl -k https://192.168.1.10:8765/api/logos
```

```json
["church-logo.png", "cross.svg", "dove.png"]
```

---

### `POST /api/logos`

Uploads a new logo image. Body is a JSON object with `name` and `data` (a base64 data URL).

```bash
curl -k -X POST https://192.168.1.10:8765/api/logos \
  -H "Content-Type: application/json" \
  -d '{ "name": "my-logo.png", "data": "data:image/png;base64,iVBORw0KGgo…" }'
```

```json
{ "file": "my-logo.png" }
```

**Errors:**
- `400 Bad Request` — No lower third folder configured in Settings, or body is malformed / missing `name` / `data`.

---

## Error Reference

| HTTP status | Meaning |
|-------------|---------|
| `200 OK` | Request succeeded |
| `400 Bad Request` | Body could not be parsed or required fields missing |
| `401 Unauthorized` | API key is wrong or missing (when auth is enabled) |
| `403 Forbidden` | Desktop user clicked **Deny** |
| `404 Not Found` | Resource (song, slide, image, presentation) does not exist |
| `500 Internal Server Error` | Unexpected server-side error |
| `503 Service Unavailable` | Data not yet loaded (e.g. Bible not loaded, no picture folder open) |

