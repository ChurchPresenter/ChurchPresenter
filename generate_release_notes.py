#!/usr/bin/env python3
"""
Generates categorized release notes from commits since the last GitHub release.
Usage: python3 generate_release_notes.py [from_tag]
"""

import subprocess
import sys
import re

# Ensure UTF-8 output on Windows
if sys.stdout.encoding != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

CATEGORIES = [
    ("Songs",          r"song|favorite|column|composer|writer|songbook"),
    ("Recents",        r"recent"),
    ("Q&A / QR",       r"qa|q&a|qr|qr code|cooldown"),
    ("Media",          r"media|video|audio|\bplay\b|\bstop\b"),
    ("Bible",          r"bible|verse|scripture"),
    ("Schedule",       r"schedule|playlist"),
    ("Pictures",       r"picture|image|photo"),
    ("Presentations",  r"presentation|slide|powerpoint|pdf"),
    ("Displays",       r"display|screen|lock|output"),
    ("Localization",   r"language|translation|locali|i18n|string"),
    ("Settings",       r"setting|config|preference|theme"),
    ("Build",          r"submodule|ci|build|gradle|workflow"),
    ("UI / General",   r""),
]

NOISE = re.compile(
    r"^(bug fix|moved? button pos|made button clickable|wip|temp|fix|update|fixes|cleanup)$",
    re.IGNORECASE,
)

def run(cmd):
    return subprocess.check_output(cmd, text=True).strip()

def last_release_tag():
    if len(sys.argv) > 1:
        return sys.argv[1]
    return run(["gh", "release", "list", "--limit", "2", "--json", "tagName", "-q", ".[1].tagName"])

def categorize(msg):
    lower = msg.lower()
    for name, pattern in CATEGORIES:
        if not pattern or re.search(pattern, lower):
            return name
    return "UI / General"

def main():
    from_tag = last_release_tag()
    latest_tag = run(["git", "describe", "--tags", "--abbrev=0"])
    # If no new tag exists yet, show commits from last release to HEAD
    end_ref = "HEAD" if latest_tag == from_tag else latest_tag
    version = latest_tag.lstrip("v") if end_ref != "HEAD" else "unreleased"

    print(f"Release notes: {from_tag} → {end_ref}")
    print("---\n")

    raw = run(["git", "log", f"{from_tag}..{end_ref}", "--pretty=format:%s"])
    commits = [line for line in raw.splitlines() if line and not NOISE.match(line.strip())]

    buckets = {name: [] for name, _ in CATEGORIES}
    for msg in commits:
        buckets[categorize(msg)].append(msg)

    for name, _ in CATEGORIES:
        items = buckets[name]
        if items:
            print(f"**{name}**")
            for item in items:
                print(f"- {item}")
            print()

    print("---")
    print("Download the installer for your platform below.\n")
    print(f"- **Windows:** ChurchPresenter-{version}-WINDOWS-x64.msi")
    print(f"- **macOS:** ChurchPresenter-{version}-MACOS-arm64.dmg")
    print(f"- **macOS:** ChurchPresenter-{version}-MACOS-x64.dmg")
    print(f"- **Linux:** churchpresenter_{version}_amd64-DEBIAN-x64.deb")

if __name__ == "__main__":
    main()
