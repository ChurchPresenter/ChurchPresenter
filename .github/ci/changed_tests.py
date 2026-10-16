#!/usr/bin/env python3
"""The test classes a change added or edited, grouped by the Gradle task that runs them.

    python3 .github/ci/changed_tests.py --base <sha>

AGENT.md's flake rule -- a new or changed test passes three consecutive runs -- is enforced by
running what this prints three times. One line per test task:

    :lower-third:test org.churchpresenter.lowerthird.LowerThirdTabTest org.churchpresenter...

A test file is a Kotlin file under `<module>/src/test/kotlin/` (`src/jvmTest/kotlin/` in
:composeApp) whose name ends in `Test`. Its classes are the top-level classes it declares whose names
end in `Test` -- a file may be named for what it tests rather than for its class -- or, when it
declares none, the file name; each is qualified by the file's `package` line. Added, modified, or
renamed and edited: a file git sees as renamed unchanged was proven where it was.
Screenshot suites are left out (they render and encode, and CI's Linux pictures are advisory), as
are the helpers beside the tests (`*TestSupport`, fixtures), which are not classes to run.

Prints nothing when there is no base to compare against: three runs of the whole suite is not what
the rule asks for.
"""
import argparse
import os
import re
import subprocess
import sys

NO_BASE = ("", "0" * 40)

# <module dir>/src/<source set>/kotlin/<path>/<Name>Test.kt
TEST_FILE = re.compile(r"^(?P<module>[^/]+)/src/(?P<source_set>test|jvmTest)/kotlin/.+/(?P<name>[A-Za-z0-9_]+Test)\.kt$")
PACKAGE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)", re.MULTILINE)
# A top-level class whose name ends in `Test`: at the start of a line, after any modifiers.
TEST_CLASS = re.compile(r"^(?:(?:internal|open|abstract)\s+)*class\s+([A-Za-z0-9_]+Test)\b", re.MULTILINE)


def changed_files(base):
    if base in NO_BASE:
        return None
    result = subprocess.run(
        ["git", "diff", "--name-status", "-M", "--diff-filter=AMR", base, "HEAD"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return None
    return changed_paths(result.stdout)


def changed_paths(name_status):
    """The paths `git diff --name-status -M` reports as added, modified, or renamed with edits.

    A rename scored below 100 carries edits, so its new path counts; one at 100 moved unchanged.
    """
    paths = []
    for line in name_status.splitlines():
        fields = line.split("\t")
        status = fields[0]
        if status in ("A", "M") and len(fields) == 2:
            paths.append(fields[1])
        elif status.startswith("R") and status != "R100" and len(fields) == 3:
            paths.append(fields[2])
    return paths


def task_for(module, source_set):
    if module == "composeApp":
        return ":composeApp:jvmTest" if source_set == "jvmTest" else None
    return f":{module}:test" if source_set == "test" else None


def test_classes(paths, read=lambda p: open(p, encoding="utf-8").read()):
    """{task: [fully qualified class, ...]} for the test files among [paths]."""
    tasks = {}
    for path in paths:
        match = TEST_FILE.match(path)
        if not match or "ScreenshotTest" in match["name"]:
            continue
        task = task_for(match["module"], match["source_set"])
        if task is None:
            continue
        try:
            text = read(path)
        except OSError:
            continue
        package = PACKAGE.search(text)
        for name in TEST_CLASS.findall(text) or [match["name"]]:
            tasks.setdefault(task, []).append(f"{package.group(1)}.{name}" if package else name)
    return {task: sorted(set(classes)) for task, classes in tasks.items()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="")
    args = parser.parse_args()

    files = changed_files(args.base)
    if files is None:
        print("No base to compare against: no changed tests to repeat.", file=sys.stderr)
        return
    groups = test_classes([f for f in files if os.path.isfile(f)])
    for task in sorted(groups):
        print(task, *groups[task])
    count = sum(len(c) for c in groups.values())
    print(f"Changed test classes: {count} in {len(groups)} task(s)", file=sys.stderr)


if __name__ == "__main__":
    main()
