# Budgets

What the app may cost on the reference Mac (Apple Silicon, 10 CPUs, macOS 26), measured 2026-10-08.
Each budget is the measured value with headroom; `./gradlew :composeApp:startupBenchmark
-PcheckBudgets` fails when a median passes its row below, and the soak fails on its own limits.
Re-measure on the reference machine before changing a number.

## Startup and idle (`startupBenchmark`, 5 launches, the first not counted)

| Key | Budget | Measured median | What it is |
|---|---:|---:|---|
| `toMainMs` | 200 | 46 | JVM start to `main()` |
| `toFirstFrameMs` | 2500 | 1948 | JVM start to the main window's first frame |
| `idleHeapMb` | 120 | 61.8 | Java heap after a collection, 30 s after the first frame |
| `idleRssMb` | 800 | 629.6 | Resident memory at the same moment |

## One hour of service (`soakTest -PsoakMinutes=60`)

| | Budget | Measured |
|---|---:|---:|
| Heap growth, first quarter to last | 64 MB | -4.1 MB |
| Resident memory growth | 256 MB | not reported on macOS; judged on CI's Linux run |
| Worst frame after warm-up | 250 ms | 74.4 ms |
| UI-thread stalls past 250 ms | 0 | 0 (longest answer 62 ms) |

These are `SoakLimits`; the soak enforces them on every run, weekly on CI.

## The UI watchdog (`gpuBenchmark` with and without `-PgpuBenchmarkWatchdog`)

Measured 2026-10-15 with the watchdog pinging the event thread at its live budget, 100 ms: across
the 13 on-screen scenarios at 1080p the typical frame stays 16.66–16.67 ms, p99 moves by at most
0.4 ms either way, and dropped frames are the same (one, the lottie lower third, in both runs). No
budget: it costs nothing measurable. Re-measure when the watchdog changes.

## Frames

- Off-screen (NDI, OMT, Browser Source): `benchmarks/results.md`, one frame at p99 — 16.7 ms at
  1080p, 33.3 ms at 4K.
- On-screen output windows: `benchmarks/gpu/results.md`. Every content type holds the display's
  refresh at 1080p (p99 18.7 ms at 60 Hz); a dropped frame is one more than 1.5 refreshes after the
  last. Reference only, not a gate: a hosted runner has no GPU, and the 4K row needs a screen that
  holds it.
