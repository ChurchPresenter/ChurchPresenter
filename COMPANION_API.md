# ChurchPresenter Companion API

The desktop app includes a built-in HTTP + WebSocket server that allows mobile companion apps (or any HTTP client) to browse song books, the Bible, and the live schedule in real time.

---

## Enabling the Server

1. Open **Settings** (menu bar → Settings, or the gear icon in the toolbar)
2. Go to the **Server** tab
3. Toggle **Enable Server** to ON
4. Note the **URL** shown (e.g. `http://192.168.1.10:8765`) — this is the address mobile clients connect to
5. Click **Save**

> The server is **disabled by default**. It only starts when you explicitly enable it.

---

## Connection Details

| Property | Value |
|---|---|
| Default port | `8765` |
| Protocol | HTTP/1.1 + WebSocket |
| Base URL | `http://{desktop-ip}:{port}` |
| WebSocket URL | `ws://{desktop-ip}:{port}/ws` |

The desktop IP address is shown in the Server settings tab once the server is running.

---

## Authentication (Optional)

If **Require API Key** is enabled in Settings → Server:

- Every request must include the API key as either:
  - HTTP header: `X-Api-Key: your-key-here`
  - Query parameter: `?apiKey=your-key-here`
- REST requests without a valid key receive **HTTP 401**
- WebSocket connections without a valid key receive `{"error":"Unauthorized"}` and are closed

If API key is **not** enabled, all endpoints are open with no authentication required.

---

## REST Endpoints

### `GET /api/info`
Returns basic server information.

**Response:**
```json
{
  "name": "ChurchPresenter",
  "version": "1.0",
  "port": 8765
}
```

---

### `GET /api/songs`
Returns the full song catalog grouped by song book.

**Query parameters:**

| Parameter | Description |
|---|---|
| `songbook` | (optional) Filter to a single song book by exact name |

**Response:**
```json
{
  "song-book": [
    {
      "book-name": "Hymns & Psalms",
      "song-total": 420,
      "songs": [
        { "number": "1", "title": "Amazing Grace", "tune": "NEW BRITAIN", "author": "John Newton" },
        { "number": "2", "title": "How Great Thou Art", "tune": "", "author": "" }
      ]
    },
    {
      "book-name": "New Songs",
      "song-total": 87,
      "songs": [
        { "number": "1", "title": "Oceans", "tune": "", "author": "" }
      ]
    }
  ],
  "songBooks": 2,
  "total": 507
}
```

**Filtered example** — get only songs from one book:
```
GET /api/songs?songbook=Hymns%20%26%20Psalms
```

---

### `GET /api/bible`
Returns the Bible structure — books, chapters, and verse counts. **Verse text is not included** to keep the response small. The mobile app uses this to build book/chapter/verse pickers, then requests the desktop to navigate to the selected verse via WebSocket.

**Query parameters:**

| Parameter | Description |
|---|---|
| `book` | (optional) Filter by book name (e.g. `Genesis`) |
| `chapter` | (optional, requires `book`) Filter to a single chapter number |

**Full response:**
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
      "book-id": 2,
      "book-name": "Exodus",
      "chapter-total": 40,
      "chapters": [
        { "chapter": 1, "verse-total": 22 }
      ]
    }
  ],
  "book-total": 66,
  "verse-total": 31102
}
```

**Single book:**
```
GET /api/bible?book=Genesis
```

**Single chapter:**
```
GET /api/bible?book=Genesis&chapter=1
```

---

### `GET /api/schedule`
Returns the current schedule — only song-type items are included.

**Response:**
```json
{
  "songs": [
    { "id": "abc-123", "songNumber": 42, "title": "Amazing Grace", "songbook": "Hymns & Psalms" },
    { "id": "def-456", "songNumber": 7,  "title": "How Great Thou Art", "songbook": "Hymns & Psalms" }
  ],
  "total": 2
}
```

---

## WebSocket

Connect to `ws://{desktop-ip}:{port}/ws` for real-time updates.

If API key is required, append it as a query parameter:
```
ws://192.168.1.10:8765/ws?apiKey=your-key-here
```

### On Connect

The server immediately pushes 3 events to every new client:

1. `songs_updated` — full song catalog
2. `bible_updated` — full Bible structure
3. `schedule_updated` — current schedule

### Events: Server → Client

All messages have this shape:
```json
{ "type": "event_name", "payload": "<JSON string>" }
```

The `payload` field is a **JSON-encoded string** — you must parse it a second time.

| `type` | Payload type | Fired when |
|---|---|---|
| `songs_updated` | `SongCatalogResponse` | Songs are loaded or settings change |
| `bible_updated` | `BibleCatalogResponse` | Bible is loaded or primary translation changes |
| `schedule_updated` | `ScheduleResponse` | Schedule changes (song added/removed/reordered) |

**Example message received:**
```json
{
  "type": "songs_updated",
  "payload": "{\"song-book\":[{\"book-name\":\"Hymns\",\"song-total\":420,\"songs\":[...]}],\"songBooks\":1,\"total\":420}"
}
```

### Commands: Client → Server

Send a JSON message with a `type` and `payload` to control the desktop app.

#### `select_song` — navigate to a song on the desktop

```json
{
  "type": "select_song",
  "payload": "{\"id\":\"\",\"songNumber\":42,\"title\":\"Amazing Grace\",\"songbook\":\"Hymns & Psalms\"}"
}
```

The `payload` is a JSON-encoded `ScheduleSongDto`:

| Field | Type | Description |
|---|---|---|
| `id` | string | Can be empty string `""` |
| `songNumber` | int | Song number within the song book |
| `title` | string | Song title (used for matching) |
| `songbook` | string | Exact song book name |

---

## Recommended Mobile App Flow

### Songs
1. Connect to WebSocket → receive `songs_updated`
2. Parse `payload` → show list of song books from `song-book[].book-name`
3. User picks a song book → filter songs from that book's `songs[]`
4. User picks a song → send `select_song` command → desktop navigates to that song

Or use REST for a one-time fetch:
```
GET /api/songs
GET /api/songs?songbook=Hymns%20%26%20Psalms
```

### Bible
1. Connect to WebSocket → receive `bible_updated`
2. Parse `payload` → show list of books from `books[].book-name` and `books[].book-id`
3. User picks a book → show chapters from `chapters[].chapter` and `chapters[].verse-total`
4. User picks chapter + verse → send a `select_song` command (or future `select_verse` command) to navigate the desktop

Or use REST:
```
GET /api/bible
GET /api/bible?book=John
GET /api/bible?book=John&chapter=3
```

---

## Quick Test with `curl`

```bash
# Check server is running
curl http://192.168.1.10:8765/api/info

# Get all song books and songs
curl http://192.168.1.10:8765/api/songs

# Get songs from a specific book
curl "http://192.168.1.10:8765/api/songs?songbook=Hymns%20%26%20Psalms"

# Get Bible structure
curl http://192.168.1.10:8765/api/bible

# Get a single book
curl "http://192.168.1.10:8765/api/bible?book=Genesis"

# Get a single chapter
curl "http://192.168.1.10:8765/api/bible?book=Genesis&chapter=1"

# Get current schedule
curl http://192.168.1.10:8765/api/schedule

# With API key
curl -H "X-Api-Key: your-key-here" http://192.168.1.10:8765/api/songs
# or
curl "http://192.168.1.10:8765/api/songs?apiKey=your-key-here"
```

---

## Quick Test with WebSocket (`websocat`)

```bash
# Install websocat: https://github.com/vi/websocat
# Connect and watch live events
websocat ws://192.168.1.10:8765/ws

# With API key
websocat "ws://192.168.1.10:8765/ws?apiKey=your-key-here"

# Send a select_song command
echo '{"type":"select_song","payload":"{\"id\":\"\",\"songNumber\":1,\"title\":\"Amazing Grace\",\"songbook\":\"Hymns\"}"}' \
  | websocat ws://192.168.1.10:8765/ws
```

---

## Data Types Reference

### `SongDto`
```json
{ "number": "1", "title": "Amazing Grace", "tune": "NEW BRITAIN", "author": "John Newton" }
```

### `SongbookEntry`
```json
{ "book-name": "Hymns & Psalms", "song-total": 420, "songs": [ ... ] }
```

### `SongCatalogResponse`
```json
{ "song-book": [ ... ], "songBooks": 2, "total": 507 }
```

### `BibleBookDto`
```json
{ "book-id": 1, "book-name": "Genesis", "chapter-total": 50, "chapters": [ ... ] }
```

### `BibleChapterDto`
```json
{ "chapter": 1, "verse-total": 31 }
```

### `BibleCatalogResponse`
```json
{ "translation": "KJV", "books": [ ... ], "book-total": 66, "verse-total": 31102 }
```

### `ScheduleSongDto`
```json
{ "id": "abc-123", "songNumber": 42, "title": "Amazing Grace", "songbook": "Hymns & Psalms" }
```

### `ScheduleResponse`
```json
{ "songs": [ ... ], "total": 2 }
```

### `WebSocketMessage`
```json
{ "type": "songs_updated", "payload": "<JSON string>" }
```

---

## Notes

- The server binds to **all network interfaces** (`0.0.0.0`) so it is reachable from any device on the same network
- The server URL displayed in Settings → Server shows the **local network IP** — use this IP on mobile devices
- Song and Bible data is loaded **automatically** when the app starts, so the API is populated even before the user navigates to those tabs
- Bible data refreshes automatically when you change the primary Bible translation in Settings
- Song data refreshes automatically when you change the song storage directory in Settings

