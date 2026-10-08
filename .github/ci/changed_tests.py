#!/usr/bin/env python3
"""The test classes a change added or edited, grouped by the Gradle task that runs them.

    python3 .github/ci/changed_tests.py --base <sha>

AGENT.md's flake rule -- a new or changed test passes three consecutive runs -- is enforced by
running what this prints three times. One line per test task:

    :lower-third:test org.churchpresenter.lowerthird.LowerThirdTabTest org.churchpresenter...

A test class is a Kotlin file under `<module>/src/test/kotlin/` (`src/jvmTest/kotlin/` in
:composeApp) whose name ends in `Test`; its fully qualified name is its `package` line plus the
file name. Added or modified only: a file git sees as renamed was proven where it was.
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


def changed_files(base):
    if base in NO_BASE:
        return None
    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=AM", base, "HEAD"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return None
    return [line for line in result.stdout.splitlines() if line]


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
            package = PACKAGE.search(read(path))
        except OSError:
            continue
        fqcn = f"{package.group(1)}.{match['name']}" if package else match["name"]
        tasks.setdefault(task, []).append(fqcn)
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
