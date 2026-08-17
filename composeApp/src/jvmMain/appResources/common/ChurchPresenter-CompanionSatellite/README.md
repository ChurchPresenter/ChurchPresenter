# ChurchPresenter-CompanionSatellite

A pure-Kotlin/JVM client for [Bitfocus Companion](https://bitfocus.io/companion)'s **Satellite**
protocol — the same protocol used by the official Companion Satellite app and by hardware
surfaces like Stream Decks. It lets a host application register as a Satellite surface, receive
the live button grid (bitmaps, text, colors) Companion renders, and send button presses back.

This module is used by [ChurchPresenter](https://github.com/ChurchPresenter/ChurchPresenter) to
let the app act as a Companion surface: pick a Companion instance on the network, see its buttons
rendered inside the app, and click them to trigger whatever actions/feedbacks are configured on
the Companion side.

## Design

- No UI-toolkit dependency. `CompanionSatelliteClient` reports button updates as raw RGB bytes
  (`CompanionButtonUpdate`) via plain callbacks — the consumer decides how to render them (Compose,
  a CLI, a test).
- Registers using Companion's legacy/plain-grid `ADD-DEVICE` form (`KEYS_TOTAL`/`KEYS_PER_ROW`/
  `BITMAPS`, no `LAYOUT_MANIFEST`) — simpler than modeling Companion's full JSON surface-manifest
  schema, and still fully supported server-side by current Companion versions.
- Plain `java.net.Socket` (TCP, default port `16622`), no external protocol library — Companion's
  wire format is a simple `COMMAND key=value key2="quoted value"` line protocol.

## Usage

```kotlin
val client = CompanionSatelliteClient(
    onStatusChanged = { status, error -> /* update UI */ },
    onButtonUpdated = { update -> /* decode update.bitmapRgb and render */ },
    onButtonsReset = { count -> /* rebuild a blank grid of this size */ }
)

client.connect(host = "192.168.1.50", port = 16622, deviceId = "my-surface", rows = 4, columns = 8, bitmapSize = 72)
client.pressButton(index = 5)
client.disconnect()
client.dispose() // when done with the client entirely
```

## Building standalone

```shell
./gradlew build
./gradlew test
```

## License

GNU GPL v3, matching the main ChurchPresenter project.
