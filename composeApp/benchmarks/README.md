# Render benchmark baseline

`results.md` and `results.json` are the committed output of `RenderBenchmark`
(`composeApp/src/jvmTest/.../benchmark/`): how long each content type takes on an off-screen
output (NDI, OMT, Browser Source) at 1080p and 4K, split into render and pixel readback.

```bash
./gradlew :composeApp:renderBenchmark                         # report to build/reports/render-benchmark/
./gradlew :composeApp:renderBenchmark -PrecordRenderBaseline  # overwrite the files here
./gradlew :composeApp:renderBenchmark -PenforceRenderBudget   # fail past one frame at p99
```

- **Compare like with like.** The header of `results.md` names the machine. Numbers from another
  machine, or from a shared CI runner, are not comparable with these; re-record on the reference
  machine before reading a change into them.
- **The budget** is one frame for render and readback together at p99: 16.7 ms at 1080p (60 fps),
  33.3 ms above it (30 fps). Over-budget rows are marked; they are not yet a CI gate.
- **Not covered**: the on-screen output windows (GPU, not the CPU raster measured here), video,
  web pages, cameras and network sources, which need devices or native runtimes a build machine
  lacks.

## Soak test

`ServiceSoak`, beside the benchmark, runs the same content as a service: every content type in
turn, a cue every 20 seconds, on one long-lived 1080p output paced at 30 fps in real time. Every
minute it collects garbage and samples the heap, resident memory and that minute's frame times.

```bash
./gradlew :composeApp:soakTest                    # four hours
./gradlew :composeApp:soakTest -PsoakMinutes=10   # a short local run
```

It writes `soak.md` (verdict, and the worst frame per content type), `soak.csv` and `soak.svg` to
`build/reports/soak/`, and fails when, after the first pass through every content type:

- any frame takes longer than 250 ms, or
- the heap grows more than 64 MB, or resident memory more than 256 MB, from the first quarter of
  the run to the last (judged from eight samples up).

`.github/workflows/soak.yml` runs the four hours nightly when `main` has moved, and uploads the
report either way.
