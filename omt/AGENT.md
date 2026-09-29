# `:omt` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**OMT** — Open Media Transport, the open-source IP video protocol — as a plain Kotlin library, both
directions: load `libomt`, create a named source, push frames at it, count who is connected, tear it
down; and coming the other way, discover who else is sending and pull one of those sources back in
as packed ARGB. A real Gradle module of this build: `include(":omt")`, `implementation(projects.omt)`.

The package is `org.churchpresenter.omt`. It is `:ndi`'s sibling and deliberately the same shape —
read `ndi/AGENT.md` for the reasoning behind the seam, the reused buffers and the single-driver
rule, which apply here unchanged. This file records where OMT differs.

The app-side wiring is in `:composeApp`, as NDI's is: `OmtVideoRenderer`, `OmtOutputRegistry` and
`OmtManager` for outputs, `ProjectionOmtCard` for their settings, and `OmtFrameCache` and
`SceneOmtEditor` for the Canvas source. `OmtFrameCache` and `NdiFrameCache` share their capture
loop through `ReceivedFrameCache`.

## **The app ships libomt, unlike libndi**

`libomt` and `libvmx` are MIT, so they go inside the installer — fetched at build time from the pins
in `gradle/omt-builds.properties` into `composeApp/src/jvmMain/appResources/<os>/omt/`, with their
licence beside them, exactly as ffmpeg is (`THIRD_PARTY_OMT.md` is the disclosure). The path setting
is an **override** of the bundled copy, the way `ffmpegPath` is, not a search for something the
operator has to install.

- **Windows and macOS** come from the publisher's binary release, unmodified.
- **Linux** has no published binary, so `.github/workflows/omt-linux.yml` builds one from pinned
  commits and attaches it to a release of this repository; `linux-x86_64` in the properties file
  pins that — release `omt-libs-1.0.0.19`, built and loopback-tested by the workflow on
  2026-09-29. A version bump means running the workflow again with `publish` ticked and re-pinning.
- **`libomt` is a .NET NativeAOT library** that resolves `libvmx` by name at run time — there is no
  link-time dependency for the loader to follow. `JnaOmtLibrary.load` therefore loads `libvmx` from
  beside `libomt` first, by full path, so it is already in the process when `libomt` asks. On macOS
  NativeAOT also finds it on its own (verified); on Windows the preload is what `LoadLibrary`'s
  already-loaded check matches, and on Linux the Linux build gives `libvmx.so` a soname so glibc's
  does. **The two files must always land in the same directory.**
- The macOS release is only ad-hoc signed. `signBundledOmt` re-signs both dylibs with the Developer
  ID for notarization; they take no entitlements of their own, the app's apply.

## How OMT differs from NDI

- **No runtime to bring up and no finder handle.** Discovery is one process-wide call,
  `omt_discovery_getaddresses`, started on first use and kept for the life of the process. So there
  is no `NdiSourceDirectory`-style refcounting and no use-after-free on close. What remains is the
  array it returns, which is valid only until the next call: `OmtDiscovery` serialises every look.
- **Alpha rides in the one stream.** A BGRA frame flagged `OMTVideoFlags_Alpha` arrives keyed, so
  there are two output modes, not three — no separate key source.
- **Quality is a sender setting** (`OmtQuality`), and `DEFAULT` means "the highest any receiver
  asks for", which is why it is the default.
- **Connections, not receivers.** `omt_send_connections` counts a receiver's video connection and,
  if it takes audio, a second one. `OmtSender.receiverCount` reports the connection count, which is
  right for "is anyone watching" and an upper bound otherwise. A video-only receiver — this app's own
  — measured as exactly one.
- **On Linux, no Avahi daemon means an aborted process** — an upstream `libomtnet` bug
  (`OMTDiscoveryAvahi.cs`: a failed `avahi_client_new` is logged and its null client passed to
  `avahi_service_browser_new` anyway, which fails Avahi's assertion and aborts). It fires the moment
  discovery starts, so any sender, receiver or look would take the app down, and a discovery server
  does not avoid it. `OmtRuntime.discoveryServiceAvailable` checks the daemon's socket **before the
  library is loaded**, and `OmtRuntimeHost` reports `DiscoveryServiceMissing` instead of loading it;
  the card tells the operator to start `avahi-daemon`. Found by `omt-linux.yml`'s loopback test on a
  runner without Avahi — which is why that workflow now installs and starts it.
- **The discovery server is read once**, when `libomtnet` creates its discovery singleton
  (`OMTDiscovery`'s constructor reads `DiscoveryServer`), so `OmtRuntimeHost.start` sets it before
  anything discovers and a change takes effect at the next launch. A blank setting is **not**
  written: that would override a server configured in OMT's own `settings.xml`, which every OMT app
  on the machine shares.
- **A receiver keeps its last frame after the source goes away** — OBS's OMT plugin does (found
  testing on Windows). So `OmtVideoRenderer.stop` sends one blank frame (transparent in Alpha,
  black in Fill) before closing, when anyone is watching; `OmtHardwareTest` checks against the real
  library that this blank is the last thing a receiver gets.
- **OBS opens a second connection** beside its video one. One Windows run saw it drop after about a
  minute; another saw it still open after several — so an OBS receiver often reads as "2 receiving",
  and OBS plus a Canvas layer as 3.
- **A vanished source is retried by the library itself**, every couple of seconds, logging each
  attempt — about 3 KB a minute. Retrying is right (the source may come back), so `OmtManager`
  bounds the log instead: each run starts `omt.log` fresh and keeps the last run as `omt.log.1`.
- **The library writes a log** into `~/.OMT/logs` — `C:\ProgramData\OMT\logs` on Windows — one
  file per process, unless told otherwise.
  `OmtManager` points it at `~/.churchpresenter/omt.log`; `setLoggingFilename` must be the first call.
- **`omt_shutdown` is never called.** The header allows it only after every sender *and receiver*
  is gone, and a Canvas capture loop can still be inside `omt_receive` when the shutdown hook runs.
  The hook stops the senders; process exit takes the library's threads.
- **A received frame needs no freeing.** The library owns it until the next `omt_receive` on that
  receiver; `JnaOmtLibrary` copies it out row by row, honouring `Stride`, before returning.
- **A receiver connects by either form** — the `HOSTNAME (Name)` discovery reports, or an
  `omt://host:port` URL — so the Canvas source stores one `sourceAddress` and lets the operator type
  a URL for a source discovery cannot see.

## Layout

| File | Owns |
|---|---|
| `OmtRuntime.kt` | Where `libomt` is: the override, the bundled directory, the system directories. Pure over its arguments |
| `OmtRuntimeStatus.kt` | The four outcomes of looking — one of them Linux's missing Avahi — and `OmtRuntimeHost`, one library per process, handing out senders and receivers |
| `OmtLibrary.kt` | The native calls as an interface, plus `OmtVideoFrame`. **The seam** |
| `JnaOmtLibrary.kt` | The only file that knows JNA exists: the C symbols, the two ABI structs, the native buffers, the `libvmx` preload |
| `OmtSender.kt` | One source on the network |
| `OmtReceiver.kt` | One source being received — the connection, the conversion and the reused buffer |
| `OmtDiscovery.kt` | The one serialised look at what discovery has found |
| `OmtFormats.kt` | The FourCC codes, `OmtOutputMode` and `OmtQuality` |
| `OmtPixels.kt` | ARGB ⇄ BGRA, into a buffer the caller reuses |

## Rules

Everything in `ndi/AGENT.md`'s Rules applies, with the names changed. In particular:

- **Nothing here may read a setting, touch a ViewModel, or import Compose.** The stored `omtMode` and
  `omtQuality` strings are `:settings`' `Constants.OMT_*`; `OmtVideoRenderer` maps them.
- **Every native call goes through `OmtLibrary`.**
- **`@Structure.FieldOrder` and the field names are the ABI.** `OmtMediaFrameStruct` follows
  `OMTMediaFrame` field for field; every C enum in it is an `int`. A wrong order is not an error, it
  is a wrong picture or no picture — `OmtHardwareTest` is the only thing that can catch it.

## Commands

```bash
./gradlew :omt:test
./gradlew :omt:detekt                            # gate — no baseline, must be clean
./gradlew :omt:jacocoTestCoverageVerification
./gradlew :composeApp:fetchBundledOmt            # the pinned libraries into appResources
```

## Testing against the real library

Both are opt-in and inert by default, gated for the reasons `NdiHardwareTest` is — they load a
native library and advertise a source every OMT receiver on the LAN can see.

```bash
# The binding: send in both modes, then a full loopback — discover, receive, check red and alpha.
./gradlew :omt:test -PomtHardware=true --tests '*OmtHardwareTest*'
# Receive a running source (say, one of the app's outputs) and report size, frames and alpha.
./gradlew :omt:test -PomtHardware=true -PomtSource='(Lyrics)' --tests '*OmtLiveReceiverProbe*'
```

Both bind the bundled copy unless `-PomtLibrary=/dir` names another; `omt-linux.yml` runs the first
against the Linux binaries it has just built.

**Verified 2026-09-28 against v1.0.0.19 on macOS (Apple Silicon)**: the loopback test passed first
time — a 16x16 half-transparent red frame read back as `80ff0100`, alpha intact — and an app OMT
output at 640x360 was discovered as `AIS-MAC-MINI.LOCAL (Verify)` and received from a separate
process, frames flagged as carrying alpha.

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**, main and test in scope.
- **Coverage**: the root build's default six counters at 85%, with no floors and no excludes, as
  `:ndi` has it — the JNA marshalling is covered against a stand-in `OmtLibC`, leaving
  `JnaOmtLibrary.load` the one uncovered call.

## Dependencies

`jna` and `:diagnostics` (CrashReporter, at the one load site). Nothing else — no Compose, no Ktor,
no settings.
