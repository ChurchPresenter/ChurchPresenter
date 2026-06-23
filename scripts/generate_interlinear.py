#!/usr/bin/env python3
"""
Downloads tagged Greek NT (TAGNT) and Hebrew OT (TAHOT) from STEPBible-Data
(CC BY 4.0) and converts them to compact JSON for ChurchPresenter interlinear.

Output:
  ../composeApp/src/jvmMain/composeResources/files/dictionary/interlinear_g.json
  ../composeApp/src/jvmMain/composeResources/files/dictionary/interlinear_h.json

Format: [{"r":"BBBCCCVVV","w":[{"t":"word","s":"G1234"},...]}]
  r = zero-padded book(3)+chapter(3)+verse(3), 1-based
  t = inflected word form  |  s = Strong's number (G123 / H456)
"""

import json
import re
import sys
import urllib.request
from collections import OrderedDict
from pathlib import Path

# --- STEPBible Translators Amalgamated URLs (CC BY 4.0) ---
BASE = "https://raw.githubusercontent.com/STEPBible/STEPBible-Data/master/Translators%20Amalgamated%20OT%2BNT/"
TAGNT_URLS = [
    BASE + "TAGNT%20Mat-Jhn%20-%20Translators%20Amalgamated%20Greek%20NT%20-%20STEPBible.org%20CC-BY.txt",
    BASE + "TAGNT%20Act-Rev%20-%20Translators%20Amalgamated%20Greek%20NT%20-%20STEPBible.org%20CC-BY.txt",
]
TAHOT_URLS = [
    BASE + "TAHOT%20Gen-Deu%20-%20Translators%20Amalgamated%20Hebrew%20OT%20-%20STEPBible.org%20CC%20BY.txt",
    BASE + "TAHOT%20Jos-Est%20-%20Translators%20Amalgamated%20Hebrew%20OT%20-%20STEPBible.org%20CC%20BY.txt",
    BASE + "TAHOT%20Job-Sng%20-%20Translators%20Amalgamated%20Hebrew%20OT%20-%20STEPBible.org%20CC%20BY.txt",
    BASE + "TAHOT%20Isa-Mal%20-%20Translators%20Amalgamated%20Hebrew%20OT%20-%20STEPBible.org%20CC%20BY.txt",
]

OUT_DIR = Path(__file__).parent.parent / "composeApp/src/jvmMain/composeResources/files/dictionary"

BOOK_ID: dict[str, int] = {
    # OT
    "Gen": 1,  "Exo": 2,  "Lev": 3,  "Num": 4,  "Deu": 5,
    "Jos": 6,  "Jdg": 7,  "Rut": 8,  "Rth": 8,
    "1Sa": 9,  "2Sa": 10, "1Ki": 11, "2Ki": 12,
    "1Ch": 13, "2Ch": 14, "Ezr": 15, "Neh": 16, "Est": 17,
    "Job": 18, "Psa": 19, "Pro": 20, "Ecc": 21,
    "Sng": 22, "Son": 22, "Sol": 22,
    "Isa": 23, "Jer": 24, "Lam": 25, "Eze": 26, "Dan": 27,
    "Hos": 28, "Joe": 29, "Jol": 29, "Amo": 30,
    "Oba": 31, "Obd": 31,
    "Jon": 32, "Jnh": 32, "Mic": 33, "Nah": 34, "Hab": 35,
    "Zep": 36, "Hag": 37, "Zec": 38, "Mal": 39,
    # NT
    "Mat": 40, "Mrk": 41, "Luk": 42, "Jhn": 43, "Act": 44,
    "Rom": 45, "1Co": 46, "2Co": 47, "Gal": 48, "Eph": 49,
    "Php": 50, "Col": 51, "1Th": 52, "2Th": 53, "1Ti": 54,
    "2Ti": 55, "Tit": 56, "Phm": 57, "Heb": 58, "Jas": 59,
    "1Pe": 60, "2Pe": 61, "1Jn": 62, "2Jn": 63, "3Jn": 64,
    "Jud": 65, "Rev": 66,
}

# Detect data lines: first tab-column matches Book.Ch.Vs#N or Book.Ch.Vs
_REF_RE = re.compile(r"^([A-Z][a-z0-9]+)\.(\d+)\.(\d+)")


def fetch(url: str) -> str:
    print(f"  GET {url.split('/')[-1][:70]}…", flush=True)
    req = urllib.request.Request(url, headers={"User-Agent": "ChurchPresenter-Interlinear/1.0"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read().decode("utf-8")
    print(f"      -> {len(data):,} chars", flush=True)
    return data


def make_ref(book: int, chapter: int, verse: int) -> str:
    return f"{book:03d}{chapter:03d}{verse:03d}"


def parse_ref_field(field: str) -> tuple[int, int, int] | None:
    """'Mat.1.1#01=NKO' or 'Gen.1.1#01=L' → (bookId, chapter, verse)."""
    field = field.split("#")[0].split("=")[0].strip()
    m = _REF_RE.match(field)
    if not m:
        return None
    book_id = BOOK_ID.get(m.group(1))
    if book_id is None:
        return None
    return book_id, int(m.group(2)), int(m.group(3))


def normalize_strongs(raw: str) -> str | None:
    """
    Convert a STEPBible Strong's to the ChurchPresenter format (G123 / H456).
    Handles:
      - Leading zeros:  G0976  → G976
      - Letter suffix:  G2424G → G2424 (disambiguation marker)
      - Braces:         {H7225G} is handled before calling this function
    Returns None for non-standard numbers (H9000+, G9000+, unparseable).
    """
    raw = raw.strip(" {}")
    if not raw:
        return None
    m = re.match(r"^([GH])0*(\d+)[A-Za-z]?$", raw)
    if not m:
        return None
    prefix = m.group(1).upper()
    num = int(m.group(2))
    if prefix == "G" and not (1 <= num <= 5624):
        return None
    if prefix == "H" and not (1 <= num <= 8674):
        return None
    return f"{prefix}{num}"


def extract_greek_strongs(col3: str) -> str | None:
    """From TAGNT column 3 like 'G0976=N-NSF', extract 'G976'."""
    raw = col3.split("=")[0].strip()
    return normalize_strongs(raw)


def extract_hebrew_strongs(col4: str) -> str | None:
    """
    From TAHOT column 4 extract the root Strong's number.
    Formats: 'H9003/{H7225G}', '{H1254A}', '{H0430G}', 'H9009/{H8064}', '{H0853}'.
    We want the content of the deepest {} which is the lexical root.
    """
    # Find all contents in braces
    braced = re.findall(r"\{([^}]+)\}", col4)
    if braced:
        # Take the last brace group (root, not prefixes)
        return normalize_strongs(braced[-1])
    # No braces — might be a simple entry like H1254A or H0853
    raw = col4.split("\\")[0].split("/")[0].strip()
    return normalize_strongs(raw)


def clean_greek_word(col1: str) -> str:
    """'Βίβλος (Biblos)' → 'Βίβλος'."""
    word = col1.split("(")[0].strip()
    return word


def clean_hebrew_word(col1: str) -> str:
    """
    'בְּ/רֵאשִׁ֖ית' → 'בְּרֵאשִׁ֖ית'
    'הָ/אָֽרֶץ\\׃' → 'הָאָֽרֶץ'
    """
    # Strip backslash-separated suffix (verse punctuation markers)
    word = col1.split("\\")[0]
    # Remove morpheme boundary slashes
    word = word.replace("/", "")
    return word.strip()


def parse_tagnt(text: str) -> OrderedDict[str, list[dict]]:
    """Parse TAGNT Greek NT file. Returns ordered dict of ref → word list."""
    verses: OrderedDict[str, list[dict]] = OrderedDict()
    parsed = skipped = 0

    for line in text.splitlines():
        if not line or line.startswith("#") or "\t" not in line:
            continue
        parts = line.split("\t")
        if len(parts) < 4:
            continue

        ref_parsed = parse_ref_field(parts[0])
        if ref_parsed is None:
            continue
        book_id, chapter, verse = ref_parsed
        if book_id < 40:  # NT only
            continue

        strongs = extract_greek_strongs(parts[3])
        if strongs is None:
            skipped += 1
            continue

        word = clean_greek_word(parts[1])
        if not word:
            skipped += 1
            continue

        ref_key = make_ref(book_id, chapter, verse)
        if ref_key not in verses:
            verses[ref_key] = []
        verses[ref_key].append({"t": word, "s": strongs})
        parsed += 1

    print(f"      parsed {parsed:,} words, {skipped:,} skipped")
    return verses


def parse_tahot(text: str) -> OrderedDict[str, list[dict]]:
    """Parse TAHOT Hebrew OT file. Returns ordered dict of ref → word list."""
    verses: OrderedDict[str, list[dict]] = OrderedDict()
    parsed = skipped = 0

    for line in text.splitlines():
        if not line or line.startswith("#") or "\t" not in line:
            continue
        parts = line.split("\t")
        if len(parts) < 5:
            continue

        ref_parsed = parse_ref_field(parts[0])
        if ref_parsed is None:
            continue
        book_id, chapter, verse = ref_parsed
        if book_id > 39:  # OT only
            continue

        strongs = extract_hebrew_strongs(parts[4])
        if strongs is None:
            skipped += 1
            continue

        word = clean_hebrew_word(parts[1])
        if not word:
            skipped += 1
            continue

        ref_key = make_ref(book_id, chapter, verse)
        if ref_key not in verses:
            verses[ref_key] = []
        verses[ref_key].append({"t": word, "s": strongs})
        parsed += 1

    print(f"      parsed {parsed:,} words, {skipped:,} skipped")
    return verses


def merge_verse_dicts(dicts: list[OrderedDict]) -> list[dict]:
    merged: OrderedDict[str, list[dict]] = OrderedDict()
    for d in dicts:
        for ref, words in d.items():
            if ref not in merged:
                merged[ref] = words
            else:
                merged[ref].extend(words)
    return [{"r": ref, "w": words} for ref, words in merged.items()]


def verify(data: list[dict], checks: list[tuple[str, str, str]]):
    index = {v["r"]: v for v in data}
    for ref, expected_strongs, label in checks:
        verse = index.get(ref)
        if verse is None:
            print(f"  WARN: verse {ref} ({label}) not found")
            continue
        found = [w["t"] for w in verse["w"] if w["s"] == expected_strongs]
        status = f"OK {found}" if found else "WARN: Strong's not found in verse"
        print(f"  {label}: {ref} {expected_strongs} -> {status}")


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    # ── Greek NT ──────────────────────────────────────────────────────────
    print("\n=== Greek NT (TAGNT) ===")
    greek_dicts = []
    for url in TAGNT_URLS:
        text = fetch(url)
        greek_dicts.append(parse_tagnt(text))
    greek_data = merge_verse_dicts(greek_dicts)
    print(f"  Total: {len(greek_data):,} verses")

    print("  Verifying…")
    verify(greek_data, [
        ("040001001", "G976",  "Matt 1:1 biblos"),
        ("043001001", "G3056", "John 1:1 logos"),
        ("043001001", "G2316", "John 1:1 theos"),
    ])

    g_path = OUT_DIR / "interlinear_g.json"
    g_path.write_text(
        json.dumps(greek_data, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"  Wrote {g_path.name}  ({g_path.stat().st_size / 1024:.0f} KB)")

    # ── Hebrew OT ─────────────────────────────────────────────────────────
    print("\n=== Hebrew OT (TAHOT) ===")
    hebrew_dicts = []
    for url in TAHOT_URLS:
        text = fetch(url)
        hebrew_dicts.append(parse_tahot(text))
    hebrew_data = merge_verse_dicts(hebrew_dicts)
    print(f"  Total: {len(hebrew_data):,} verses")

    print("  Verifying…")
    verify(hebrew_data, [
        ("001001001", "H1254", "Gen 1:1 bara"),
        ("001001001", "H430",  "Gen 1:1 Elohim"),
        ("019023001", "H3068", "Ps 23:1 YHWH"),
    ])

    h_path = OUT_DIR / "interlinear_h.json"
    h_path.write_text(
        json.dumps(hebrew_data, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"  Wrote {h_path.name}  ({h_path.stat().st_size / 1024:.0f} KB)")

    print(f"\nDone — {len(greek_data):,} Greek verses, {len(hebrew_data):,} Hebrew verses.")


if __name__ == "__main__":
    main()
