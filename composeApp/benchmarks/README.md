# Render benchmark baselines

`RenderBenchmark` (`composeApp/src/jvmTest/.../benchmark/`) measures how long each content type
takes on an off-screen output (NDI, OMT, Browser Source) at 1080p and 4K, split into render and
pixel readback, and writes `results.md` and `results.json`. Two baselines of that output are kept,
from two different machines, and they are **never compared with each other**:

| Files | Machine | Recorded with | Used for |
|---|---|---|---|
| `results.{md,json}` here | the reference Mac named in `results.md` | `-PrecordRenderBaseline`, on that Mac | the absolute frame budget |
| `ci/results.{md,json}` | GitHub's hosted `ubuntu-latest` runner (~10x slower) | `render-benchmark.yml`, dispatched with `record_baseline` | the CI regression gate |

```bash
./gradlew :composeApp:renderBenchmark                           # report to build/reports/render-benchmark/
./gradlew :composeApp:renderBenchmark -PrecordRenderBaseline    # overwrite the reference baseline here
./gradlew :composeApp:renderBenchmark -PenforceRenderBudget     # fail past one frame at p99
./gradlew :composeApp:renderBenchmark -PrecordCiRenderBaseline  # write ci/ (only from the CI runner -- see below)
./gradlew :composeApp:renderBenchmark -PcheckRenderRegression   # fail on a row slower than ci/results.json
./gradlew :composeApp:renderBenchmark -PcheckRenderRegression=path/to/results.json  # another baseline
```

- **Compare like with like.** The header of each `results.md` names its machine. Re-record the
  reference baseline on the reference Mac before reading a change into it.
- **The budget** is one frame for render and readback together at p99: 16.7 ms at 1080p (60 fps),
  33.3 ms above it (30 fps). Over-budget rows are marked. It is not a CI gate: a hosted runner
  misses it on hardware alone.
- **Not covered**: the on-screen output windows (GPU, not the CPU raster measured here), video,
  web pages, cameras and network sources, which need devices or native runtimes a build machine
  lacks.

## The CI regression gate

`.github/workflows/render-benchmark.yml` runs on every pull request and on `main`, and holds the
run against `ci/results.json`. Each scenario and size that both have is compared on its **typical
frame** -- render p50 plus readback p50, the median rather than the tail, because a shared runner's
tail is mostly its neighbours. A row regresses when

    now > baseline x (1 + margin) + slack      margin 0.5, slack 1.0 ms

(`-PrenderRegressionMargin`, `-PrenderRegressionSlackMs`). The slack keeps a sub-millisecond row
from failing on timer noise. A row only one side has -- a new scenario, or one removed -- is listed
in the report and never fails; re-record to bring it in. The comparison is appended to the run's
`results.md`, which the job writes to its summary and uploads as the `render-benchmark` artifact.

A missing or unreadable baseline fails `-PcheckRenderRegression` rather than passing it. In CI the
job checks first: with no `ci/results.json` committed it skips the gate with a notice.

**Recording it:** dispatch the *Render benchmark* workflow with `record_baseline` ticked, download
its `ci-render-baseline` artifact and commit the two files to `composeApp/benchmarks/ci/`.
Re-record the same way after a change that is meant to make rendering slower, or when GitHub moves
`ubuntu-latest` to different hardware. Never commit a `ci/` recorded on a developer machine.

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

`.github/workflows/soak.yml` runs the four hours on demand until it has run green, and uploads the
report either way.
