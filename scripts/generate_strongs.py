#!/usr/bin/env python3
"""
Downloads the complete Strong's Hebrew and Greek dictionaries from OpenScriptures
(CC BY 4.0) and converts them to the JSON format used by ChurchPresenter.

Output:
  ../composeApp/src/jvmMain/composeResources/files/dictionary/strongs_h.json
  ../composeApp/src/jvmMain/composeResources/files/dictionary/strongs_g.json
"""

import json
import re
import urllib.request
from pathlib import Path

HEBREW_URL = "https://raw.githubusercontent.com/openscriptures/strongs/master/hebrew/strongs-hebrew-dictionary.js"
GREEK_URL  = "https://raw.githubusercontent.com/openscriptures/strongs/master/greek/strongs-greek-dictionary.js"

OUT_DIR = Path(__file__).parent.parent / "composeApp/src/jvmMain/composeResources/files/dictionary"


def fetch(url: str) -> str:
    print(f"Downloading {url} …", flush=True)
    req = urllib.request.Request(url, headers={"User-Agent": "ChurchPresenter-StrongsConverter/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read().decode("utf-8")
    print(f"  → {len(data):,} chars", flush=True)
    return data


def js_to_dict(js_text: str) -> dict:
    """Extract the first top-level JSON object from a JS file."""
    # Find the first { and the matching last }
    start = js_text.index("{")
    end   = js_text.rindex("}") + 1
    text  = js_text[start:end]
    # Fix trailing commas before } or ] (not valid JSON)
    text = re.sub(r",\s*([}\]])", r"\1", text)
    return json.loads(text)


def parse_hebrew(js_text: str) -> list[dict]:
    raw = js_to_dict(js_text)
    entries = []
    for key, val in raw.items():
        if not key.startswith("H"):
            continue
        try:
            num = int(key[1:])
        except ValueError:
            continue

        word         = val.get("lemma", "").strip()
        translit     = val.get("xlit", "").strip()
        pronunciation = val.get("pron", "").strip()
        derivation   = val.get("derivation", "").strip().rstrip(";").strip()
        strongs_def  = val.get("strongs_def", "").strip().strip("{}")
        kjv_usage    = val.get("kjv_def", "").strip().rstrip(".")

        # Combine derivation + definition for a richer definition field
        if derivation:
            definition = f"{derivation.capitalize()}; {strongs_def}".strip("; ")
        else:
            definition = strongs_def

        entries.append({
            "number": key,
            "word": word,
            "transliteration": translit,
            "pronunciation": pronunciation,
            "definition": definition,
            "kjvUsage": kjv_usage,
        })

    entries.sort(key=lambda e: int(e["number"][1:]))
    return entries


def parse_greek(js_text: str) -> list[dict]:
    raw = js_to_dict(js_text)
    entries = []
    for key, val in raw.items():
        if not key.startswith("G"):
            continue
        try:
            num = int(key[1:])
        except ValueError:
            continue

        word         = val.get("lemma", "").strip()
        # Greek uses "translit", Hebrew uses "xlit"
        translit     = val.get("translit", val.get("xlit", "")).strip()
        pronunciation = val.get("pron", "").strip()
        derivation   = val.get("derivation", "").strip().rstrip(";").strip()
        strongs_def  = val.get("strongs_def", "").strip().strip("{}")
        kjv_usage    = val.get("kjv_def", "").strip().rstrip(".")

        if derivation:
            definition = f"{derivation.capitalize()}; {strongs_def}".strip("; ")
        else:
            definition = strongs_def

        entries.append({
            "number": key,
            "word": word,
            "transliteration": translit,
            "pronunciation": pronunciation,
            "definition": definition,
            "kjvUsage": kjv_usage,
        })

    entries.sort(key=lambda e: int(e["number"][1:]))
    return entries


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    heb_js = fetch(HEBREW_URL)
    heb_entries = parse_hebrew(heb_js)
    print(f"  Parsed {len(heb_entries):,} Hebrew entries")

    gre_js = fetch(GREEK_URL)
    gre_entries = parse_greek(gre_js)
    print(f"  Parsed {len(gre_entries):,} Greek entries")

    h_path = OUT_DIR / "strongs_h.json"
    g_path = OUT_DIR / "strongs_g.json"

    h_path.write_text(json.dumps(heb_entries, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {h_path}  ({h_path.stat().st_size / 1024:.0f} KB)")

    g_path.write_text(json.dumps(gre_entries, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {g_path}  ({g_path.stat().st_size / 1024:.0f} KB)")

    print(f"\nDone — {len(heb_entries) + len(gre_entries):,} total entries.")


if __name__ == "__main__":
    main()
