"""What the Tests workflow's parallel jobs run, worked out once, in the `prepare` job.

    python3 .github/ci/plan_ci.py shards --affected affected.txt     # matrix for the `modules` job
    python3 .github/ci/plan_ci.py report-inputs                       # JUnit paths and check names

`--affected` is what `affected_modules.py` printed: one `<key>=true|false` line per module.

**The module list lives here** -- every module with a test suite, the name its JUnit check carries,
and a weight: its suite's CI minutes, measured. A new module is one row; until it has one, `shards`
names it in a workflow warning rather than leaving it untested in silence.

The affected modules are packed greedily, heaviest first, into at most `MAX_SHARDS` groups of
roughly `SHARD_MINUTES` each. Every group is one runner. Its suites compile in parallel but run one
at a time: several suites at once on one runner starve the tests that wait on a real clock (a UDP
handshake, a double-click), and runners cost nothing here, so the parallelism is runners.
"""
import argparse
import glob
import json
import math
import sys

# (directory, JUnit check name, CI minutes of its suite as measured on a 4-core runner)
MODULES = [
    ("converter", "Converter", 0.4),
    ("companion-satellite", "Companion Satellite", 0.1),
    ("lottieGenerator", "Lower Third generator", 0.5),
    ("crossword", "Crossword", 0.1),
    ("presentation-engine", "Presentation Engine", 0.4),
    ("theme", "Theme", 0.2),
    ("core-models", "Core models", 0.2),
    ("songlibrary", "Song library", 0.5),
    ("bible-engine", "Bible Lookup Engine", 0.3),
    ("settings", "Settings", 0.2),
    ("diagnostics", "Diagnostics", 0.1),
    ("atem", "ATEM client", 0.3),
    ("ndi", "NDI client", 0.1),
    ("omt", "OMT client", 0.1),
    ("planning-center", "Planning Center", 0.7),
    ("bible-formats", "Bible formats", 0.1),
    ("song-chords", "Song chords", 0.1),
    ("bible", "Bible", 0.1),
    ("calendar", "Calendar", 2.3),
    ("shared-ui", "Shared UI", 1.2),
    ("slides", "Slides", 2.7),
    ("media", "Media", 1.2),
    ("web", "Web", 0.6),
    ("crossword-tab", "Crossword tab", 0.1),
    ("qa", "QA", 0.7),
    ("obs", "OBS", 0.3),
    ("live-show", "Live show", 0.1),
    ("show-control", "Show control", 0.1),
    ("control-in", "Control in", 0.1),
    ("live-output", "Live output", 1.3),
    ("statistics", "Statistics", 0.4),
    ("updater", "Updater", 0.2),
    ("telemetry", "Telemetry", 0.4),
    ("app-settings", "App settings", 0.9),
    ("server-ui", "Server UI", 1.0),
    ("dictionary", "Dictionary", 1.0),
    ("stt", "STT", 0.4),
    ("announcements", "Announcements", 0.8),
    ("helper", "Helper", 0.8),
    ("lower-third", "Lower Third", 0.8),
    ("songs", "Songs", 1.6),
    ("bible-tab", "Bible tab", 1.9),
    ("server", "Server", 1.5),
    ("schedule", "Schedule", 1.0),
    ("canvas", "Canvas", 2.1),
    ("presenter", "Presenter", 1.0),
    ("profiles", "Profiles", 7.7),
    ("companion-surface", "Companion Surface", 0.6),
    ("dialogs", "Dialogs", 0.6),
]

# Modules in the graph that have no test suite of their own.
NO_SUITE = {"composeApp", "strings", "icons"}

# Extra tasks a module's group runs beside its suite and its coverage floor.
EXTRA_TASKS = {"helper": [":helper:wickEval"]}

MAX_SHARDS = 8
SHARD_MINUTES = 4.0


def key(directory):
    """The key affected_modules.py prints for a module directory."""
    return directory.replace("-", "").lower()


def read_affected(path):
    affected, known = set(), set()
    for line in open(path):
        name, _, value = line.strip().partition("=")
        if not name:
            continue
        known.add(name)
        if value == "true":
            affected.add(name)
    return affected, known


def shards(affected_keys):
    """The groups the affected modules run in, heaviest first, as the `modules` job's matrix."""
    chosen = [(d, w) for d, _, w in MODULES if key(d) in affected_keys]
    if not chosen:
        return []
    total = sum(w for _, w in chosen)
    count = max(1, min(MAX_SHARDS, len(chosen), math.ceil(total / SHARD_MINUTES)))
    bins = [{"minutes": 0.0, "modules": []} for _ in range(count)]
    for directory, weight in sorted(chosen, key=lambda m: (-m[1], m[0])):
        lightest = min(bins, key=lambda b: b["minutes"])
        lightest["modules"].append(directory)
        lightest["minutes"] += weight
    result = []
    for index, group in enumerate(bins, start=1):
        modules = sorted(group["modules"])
        tasks = []
        for directory in modules:
            tasks += [f":{directory}:test", f":{directory}:jacocoTestCoverageVerification"]
            tasks += EXTRA_TASKS.get(directory, [])
        result.append({
            "name": f"{index} of {count}",
            "modules": " ".join(modules),
            "tasks": " ".join(tasks),
        })
    return result


def unlisted(known_keys):
    """Modules the graph knows that have no row here, so nothing would ever run their suite."""
    listed = {key(d) for d, _, _ in MODULES} | {key(d) for d in NO_SUITE}
    return sorted(known_keys - listed)


def report_inputs(root="."):
    """JUnit report paths and check names for the modules that produced results in this run."""
    rows = [("composeApp", "App")] + [(d, n) for d, n, _ in MODULES]
    ran = [(d, n) for d, n in rows if glob.glob(f"{root}/{d}/build/test-results/**/TEST-*.xml", recursive=True)]
    return (
        "\n".join(f"{d}/build/test-results/**/TEST-*.xml" for d, _ in ran),
        "\n".join(n for _, n in ran),
    )


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    plan = sub.add_parser("shards")
    plan.add_argument("--affected", required=True)
    sub.add_parser("report-inputs")
    args = parser.parse_args()

    if args.command == "shards":
        affected, known = read_affected(args.affected)
        for name in unlisted(known):
            print(f"::warning::Module key `{name}` has no row in .github/ci/plan_ci.py, so its tests never run.",
                  file=sys.stderr)
        groups = shards(affected)
        print(f"shards={json.dumps(groups)}")
        print(f"has_modules={'true' if groups else 'false'}")
        for group in groups:
            print(f"Group {group['name']}: {group['modules']}", file=sys.stderr)
    else:
        paths, names = report_inputs()
        print("paths<<EOF")
        print(paths)
        print("EOF")
        print("names<<EOF")
        print(names)
        print("EOF")


if __name__ == "__main__":
    main()
