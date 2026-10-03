#!/usr/bin/env python3
"""Which modules' suites a change can affect, from the build's own dependency graph.

    ./gradlew -q moduleGraph > module-graph.txt
    python3 .github/ci/affected_modules.py --graph module-graph.txt --base <sha>

A module is affected when a file in its own directory changed, or when a module it depends on --
directly or transitively, test fixtures included -- is affected. The graph is `./gradlew
moduleGraph`, so an edge exists exactly when a build.gradle.kts declares it; there is no list to
keep in step.

Prints one `<key>=true|false` line per module, for $GITHUB_OUTPUT: the key is the module's
directory with the dashes dropped and lower-cased (`bible-tab` -> `bibletab`). A summary of what
changed and what runs goes to stderr.

Everything runs when there is nothing to compare against -- no base, an all-zero one (a new
branch), or one git cannot resolve -- or when a file every module is built from changed.
"""
import argparse
import subprocess
import sys

# Files every module is configured from: the version catalogue, the root build that owns the JaCoCo
# floors and `useJUnitPlatform()`, and the wrapper. A change to any of them can move any suite.
#
# Deliberately NOT here: settings.gradle.kts (it changes to `include(...)` a new module, which cannot
# affect the others, and :composeApp runs on every push anyway), config/detekt/ (no test reads it,
# and Detekt runs unfiltered), and the workflows.
SHARED_FILES = {"build.gradle.kts", "gradle/libs.versions.toml"}
SHARED_DIRS = ("gradle/wrapper/",)

# Inputs a module reads from outside its own directory without a project dependency, so the graph
# cannot show them. :crossword-tab copies the encoded puzzles :crossword writes, at build time.
EXTRA_INPUTS = {":crossword-tab": ("crossword/encoded/",)}

NO_BASE = ("", "0" * 40)


def read_graph(path):
    graph = {}
    for line in open(path):
        parts = line.split()
        if parts and parts[0] == "MODULE":
            graph[parts[1]] = set(parts[2:])
    return graph


def changed_files(base):
    if base in NO_BASE:
        return None
    result = subprocess.run(["git", "diff", "--name-only", base, "HEAD"], capture_output=True, text=True)
    if result.returncode != 0:
        return None
    return [line for line in result.stdout.splitlines() if line]


def module_of(path, graph):
    top = path.split("/", 1)[0]
    module = ":" + top
    return module if module in graph else None


def affected(files, graph):
    if files is None or any(f in SHARED_FILES or f.startswith(SHARED_DIRS) for f in files):
        return set(graph)
    changed = {m for f in files if (m := module_of(f, graph))}
    changed |= {m for m, prefixes in EXTRA_INPUTS.items() for f in files if f.startswith(prefixes)}
    consumers = {m: {c for c, deps in graph.items() if m in deps} for m in graph}
    result, stack = set(), list(changed)
    while stack:
        module = stack.pop()
        if module not in result:
            result.add(module)
            stack.extend(consumers[module])
    return result


def key(module):
    return module.lstrip(":").replace("-", "").lower()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--graph", required=True)
    parser.add_argument("--base", default="")
    args = parser.parse_args()

    graph = read_graph(args.graph)
    files = changed_files(args.base)
    runs = affected(files, graph)

    for module in sorted(graph):
        print(f"{key(module)}={'true' if module in runs else 'false'}")

    reason = "no base to compare against" if files is None else f"{len(files)} changed files"
    print(f"Affected modules ({reason}): {' '.join(sorted(runs)) or 'none'}", file=sys.stderr)


if __name__ == "__main__":
    main()
