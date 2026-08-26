# `:ndi` — NDI output

Sends this app's live content over the network as an [NDI](https://ndi.video) source, so OBS, vMix
or a hardware switcher can take Bible verses, lyrics or a lower third straight off the LAN with no
capture card.

The mode worth knowing about is **alpha**: a single sender carrying genuine per-pixel transparency,
so a lower third arrives already keyed, with no second source and no downstream keyer. SDI cannot
physically do that. Plain fill, and a discrete fill + key pair, are there for gear that expects them.

## The NDI Runtime is a separate download

**This module ships no NDI binaries and cannot** — Vizrt's licence does not allow redistributing
them. NDI works here exactly as VLC does: install the free runtime yourself, and the app finds it.

Get it from **<https://ndi.video/download-ndi-sdk/>**. The app looks in the usual install locations
and honours the `NDI_RUNTIME_DIR_V6` and `NDI_RUNTIME_DIR_V5` environment variables, so a machine
already set up for OBS's NDI plugin needs nothing done to it. There is a path override in
Projection settings for anything unusual.

Until a runtime is installed the NDI card in Projection settings says so and offers the download
link; nothing else in the app is affected.

## Running its tests

```bash
./gradlew :ndi:test
```

No runtime and no network needed — the suite drives the whole module through a plain Kotlin fake.

---

NDI® is a registered trademark of Vizrt NDI AB.
