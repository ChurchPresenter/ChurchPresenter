#!/usr/bin/env python3
"""
Builds the `catalog.json` manifest for the Holy Bible XML archive.

    https://github.com/ChurchPresenter/Holy-Bible-XML-Format

That repository is 1048 flat `.xml` files with no index of any kind, and the title, copyright and
book counts live *inside* each ~5 MB file. Without a manifest the app would have to list the GitHub
tree (rate-limited, and it yields only names and sizes) and could show neither a real title nor a
copyright until after the download. So this script reads the archive once and commits the answers
next to it: the app then fetches one ~200 KB JSON and knows everything the browse list needs.

Usage:

    python3 scripts/generate_beblia_catalog.py --repo ~/src/Holy-Bible-XML-Format

Writes `catalog.json` into the repo, ready to commit **in the same commit as any upstream merge** —
the manifest records the commit its blob hashes were read at, and the app downloads from that pinned
commit, so a manifest that lags behind `master` serves older-but-correct files rather than failing.

Re-run it whenever upstream content changes, read the coverage report, and add entries to NAME_OVERRIDES
for anything that lands in UND.
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import re
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

EBIBLE_CATALOG_URL = "https://ebible.org/Scriptures/translations.csv"
# SIL's ISO 639-3 tables. eBible's catalogue alone resolves only about two thirds of these file
# names: it is weighted towards minority languages and simply has no row for Welsh, Zulu, Slovenian
# and a hundred others the archive carries.
ISO_CODES_URL = "https://iso639-3.sil.org/sites/iso639-3/files/downloads/iso-639-3.tab"
ISO_NAMES_URL = "https://iso639-3.sil.org/sites/iso639-3/files/downloads/iso-639-3_Name_Index.tab"
ISO_MACRO_URL = "https://iso639-3.sil.org/sites/iso639-3/files/downloads/iso-639-3-macrolanguages.tab"

CACHE_DIR = Path(__file__).parent / ".cache"
CACHE_FILE = CACHE_DIR / "ebible-translations.csv"
ISO_CODES_FILE = CACHE_DIR / "iso-639-3.tab"
ISO_NAMES_FILE = CACHE_DIR / "iso-639-3_Name_Index.tab"
ISO_MACRO_FILE = CACHE_DIR / "iso-639-3-macrolanguages.tab"

SCHEMA_VERSION = 1

# The Protestant canon. Book numbers run 1-39 for the Old Testament and 40-66 for the New, globally
# and continuously, even in a New-Testament-only edition.
OT_RANGE = range(1, 40)
NT_RANGE = range(40, 67)

# The three spellings each piece of root metadata appears under, in the order they are tried. Kept
# identical to BebliaParser.kt — if one side gains a spelling, give it to the other.
TITLE_ATTRIBUTES = ("translation", "name", "language")
RIGHTS_ATTRIBUTES = ("status", "info", "version")
SOURCE_ATTRIBUTES = ("link", "site")

# Language names these files lead with that the ISO register does not answer to: misspellings
# ("Tibetian", "Galacian", "Southeren"), country names standing in for languages ("Kazakhstan",
# "Malaysian", "PapuaNewGuinea"), and autonyms the register files under another name ("Chibemba",
# "Tshivenda", "Siswati"). Consulted before the register, and matched the same way, so one entry
# resolves a whole family of editions.
#
# Deliberately incomplete. Where a name genuinely spans many languages — "Berber" is a family of
# twenty-odd, and the "Chin" editions encode their variety in an opaque publisher abbreviation — no
# entry is invented. Those stay UND, which lists and installs exactly as any other row and only
# forgoes a place in the language filter. A guessed code would read as fact.
NAME_OVERRIDES = {
    "aceh": "ACE", "aramaic": "ARC", "avar": "AVA",
    "azerbaijan": "AZE", "azerbaijan south": "AZB",
    "balochi": "BAL", "balochi southeren": "BCC",
    "baoule": "BCI", "bemba": "BEM", "chibemba": "BEM", "bodo": "BRX", "bugis": "BUG",
    "chin tedim": "CTD", "chin matupi": "HLT",
    "dogri": "DOI",
    # The macrolanguage where the file names an edition rather than a variety; the two varieties the
    # names do identify get their own codes.
    "fulfulde": "FUL", "fulfulde adamawa": "FUB", "fulfulde benin": "FUE",
    "galacian": "GLG", "ghomala": "BBJ", "gussi": "GUZ",
    "ilokano": "ILO", "jamaican": "JAM",
    "kamba": "KAM", "kazakhstan": "KAZ", "kimiiru": "MER", "kirundi": "RUN", "konkani": "KOK",
    "lango": "LAJ", "liberian kreyol": "LIR", "luo": "LUO",
    "maasai": "MAS", "malaysian": "ZSM", "mizo": "LUS",
    # The macrolanguage, so that `RomaniRMC` can then resolve to its Carpathian member. eBible's own
    # rows would otherwise pin the bare name to Vlax Romani, which the specific code contradicts.
    "romani": "ROM",
    "moldovian": "RON",
    "original greek": "GRC", "original hebrew": "HBO",
    "papua new guinea": "TPI", "papua new guinea tok pisin": "TPI",
    "siswati": "SSW", "sotho": "SOT",
    "tashelhayt morocco": "SHI", "thado": "TCZ", "tibetian": "BOD",
    "tshiluba": "LUA", "tshivenda": "VEN",
    "waray": "WAR", "zande": "ZND",
}

# Whole file names the prefix rule cannot resolve, because the language is not where it looks.
STEM_OVERRIDES = {
    # Two languages in one name — the prefix rule can only ever pick the first.
    "AmharicTigrinya2024": "TIR",
    # The language is in the edition abbreviation, not the leading token: Gikuyu.
    "KenyaGIKCL": "KIK",
    # eBible spells these the 639-3 way and the register agrees; pinned so a register revision
    # cannot quietly move them.
    "Greek1550": "GRC",
    "GreekTR": "GRC",
    "AncientGreek": "GRC",
    "Latin": "LAT",
    "LatinVulgate": "LAT",
}

# Codes the eBible catalogue has no row for at all, transcribed from
# `data/BibleLanguageNames.kt`'s UNLISTED so the two agree on what these languages are called.
UNLISTED_NAMES = {
    "Afrikaans": "AFR", "Albanian": "ALB", "Arabic": "ARA", "Basque": "BAQ",
    "Bulgarian": "BUL", "Chinese": "CHI", "Church Slavonic": "CHU", "Czech": "CZE",
    "Esperanto": "ESP", "French": "FRE", "Gaelic": "GAE", "German": "GER",
    "Scottish Gaelic": "GLA", "Gothic": "GOT", "Greek": "GRE", "Jamaican Creole": "JAM",
    "Kabyle": "KAB", "Latvian": "LAV", "Maori": "MAO", "Low German": "NDS",
    "Dutch": "NL", "Norwegian": "NOR", "Romanian": "RUM", "Croatian": "SCR",
    "Chadian Arabic": "SHU", "Swahili": "SWA", "Syriac": "SYR", "Kenyang": "XKL",
}

UNKNOWN_LANGUAGE = "UND"  # BibleCatalogNaming.UNKNOWN_LANGUAGE

# CamelCase, acronym runs and digit runs each become one token:
#   AmharicTigrinya2024 -> Amharic, Tigrinya, 2024
#   RomaniRMC           -> Romani, RMC
TOKEN_RE = re.compile(r"[A-Z]{2,}(?![a-z])|[A-Z][a-z]+|[0-9]+|[A-Z]")


def run(repo: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(repo), *args], check=True, capture_output=True, text=True
    ).stdout


def tokenize(stem: str) -> list[str]:
    return TOKEN_RE.findall(stem)


def cached_download(url: str, path: Path, refresh: bool) -> str:
    CACHE_DIR.mkdir(exist_ok=True)
    if refresh or not path.exists():
        print(f"Downloading {url} …", flush=True)
        req = urllib.request.Request(url, headers={"User-Agent": "ChurchPresenter-BebliaCatalog/1.0"})
        with urllib.request.urlopen(req, timeout=120) as r:
            path.write_bytes(r.read())
    return path.read_text(encoding="utf-8-sig")


def normalise(name: str) -> str:
    return re.sub(r"[^a-z]", "", name.lower())


class Languages:
    """
    English language name -> (uppercase code, English name).

    Layered so the most useful code wins: eBible's own spelling first, because that is what the app's
    language filter can put a name and an autonym to, then the ISO 639-3 register for everything
    eBible has never published, then the handful of codes neither carries.
    """

    def __init__(self, refresh: bool) -> None:
        self.by_name: dict[str, tuple[str, str]] = {}
        self.codes: set[str] = set()
        self.members: set[tuple[str, str]] = set()
        self._load_macrolanguages(refresh)
        self._load_iso(refresh)
        self._load_ebible(refresh)
        for name, code in UNLISTED_NAMES.items():
            self.by_name.setdefault(normalise(name), (code, name))
            self.codes.add(code)
        # Last, and overwriting: these exist precisely because the register's own answer is wrong or
        # missing. The display name comes from whatever the register calls that code, if anything.
        for name, code in NAME_OVERRIDES.items():
            existing = next((n for n, (c, _) in self.by_name.items() if c == code), None)
            self.by_name[normalise(name)] = (code, self.by_name[existing][1] if existing else "")
            self.codes.add(code)

    def _add(self, name: str, code: str, display: str, overwrite: bool) -> None:
        key = normalise(name)
        if not key or not code:
            return
        if overwrite or key not in self.by_name:
            self.by_name[key] = (code, display)
        self.codes.add(code)

    def _load_macrolanguages(self, refresh: bool) -> None:
        for row in csv.DictReader(io.StringIO(cached_download(ISO_MACRO_URL, ISO_MACRO_FILE, refresh)), delimiter="\t"):
            macro = (row.get("M_Id") or "").strip().upper()
            member = (row.get("I_Id") or "").strip().upper()
            if macro and member:
                self.members.add((macro, member))

    def is_member_of(self, macro: str, member: str) -> bool:
        return (macro.upper(), member.upper()) in self.members

    def _load_iso(self, refresh: bool) -> None:
        for row in csv.DictReader(io.StringIO(cached_download(ISO_CODES_URL, ISO_CODES_FILE, refresh)), delimiter="\t"):
            code = (row.get("Id") or "").strip().upper()
            self._add(row.get("Ref_Name") or "", code, (row.get("Ref_Name") or "").strip(), overwrite=False)
        # Print_Name/Inverted_Name carry the alternate spellings people actually write, which is what
        # a file name like `SlovakianSLB` needs.
        for row in csv.DictReader(io.StringIO(cached_download(ISO_NAMES_URL, ISO_NAMES_FILE, refresh)), delimiter="\t"):
            code = (row.get("Id") or "").strip().upper()
            self._add(row.get("Print_Name") or "", code, (row.get("Print_Name") or "").strip(), overwrite=False)

    def _load_ebible(self, refresh: bool) -> None:
        rows = list(csv.DictReader(io.StringIO(cached_download(EBIBLE_CATALOG_URL, CACHE_FILE, refresh))))
        counts: dict[str, int] = {}
        for row in rows:
            code = (row.get("languageCode") or "").strip().upper()
            if code:
                counts[code] = counts.get(code, 0) + 1
        best: dict[str, str] = {}
        for row in rows:
            code = (row.get("languageCode") or "").strip().upper()
            if not code:
                continue
            for column in ("languageNameInEnglish", "languageName"):
                name = normalise(row.get(column) or "")
                if not name:
                    continue
                # A name shared by several codes goes to the one eBible publishes most of, which is
                # the one a reader of that name almost certainly means.
                if name not in best or counts[code] > counts[best[name]]:
                    best[name] = code
                    self._add(row.get(column) or "", code, (row.get("languageNameInEnglish") or "").strip(), overwrite=True)

    def lookup(self, name: str) -> tuple[str, str] | None:
        key = normalise(name)
        if not key:
            return None
        found = self.by_name.get(key)
        if found:
            return found
        # `Somalian`/`Slovakian`/`Latvian` are how these files spell languages the register calls
        # Somali, Slovak and Latvian — try the obvious English endings before giving up.
        for suffix, replacement in (("ian", ""), ("ian", "i"), ("an", ""), ("ish", ""), ("n", "")):
            if key.endswith(suffix):
                found = self.by_name.get(key[: -len(suffix)] + replacement)
                if found:
                    return found
        return None


def is_iso_like(token: str) -> bool:
    return token.isupper() and 2 <= len(token) <= 4


def assign_language(stem: str, tokens: list[str], languages: Languages) -> tuple[str, str, str, int]:
    """Returns (code, English name, provenance, tokens consumed) — provenance for the review report."""
    if stem in STEM_OVERRIDES:
        code = STEM_OVERRIDES[stem]
        return code, "", "override", 1

    for width in (3, 2, 1):
        if len(tokens) < width:
            continue
        # A year is never part of a language name, and matching is punctuation- and digit-insensitive
        # — so without this `Afrikaans1983` matches "Afrikaans" at width 2 and swallows the edition.
        if any(token.isdigit() for token in tokens[:width]):
            continue
        found = languages.lookup(" ".join(tokens[:width]))
        if not found:
            continue
        code, display = found
        # `RomaniRMC` names the variety in the token after the one that matched, and Carpathian
        # Romani is more specific than the macrolanguage — so it wins. Strictly membership of the
        # matched macrolanguage, never merely "looks like a code": `HebrewBSI` names the Bible
        # Society of Israel, and BSI is also somebody's ISO code.
        following = tokens[width] if len(tokens) > width else None
        if following and is_iso_like(following) and languages.is_member_of(code, following):
            return following.upper(), "", "filename", width + 1
        return code, display, "filename", width

    return UNKNOWN_LANGUAGE, "", "unknown", 0


def read_xml(path: Path) -> tuple[dict[str, str], set[int]]:
    """Root attributes and the set of book numbers, without holding the document in memory."""
    root_attributes: dict[str, str] = {}
    books: set[int] = set()
    for event, element in ET.iterparse(str(path), events=("start", "end")):
        if event == "start":
            if element.tag == "bible" and not root_attributes:
                root_attributes = dict(element.attrib)
            elif element.tag == "book":
                try:
                    books.add(int((element.get("number") or "").strip()))
                except ValueError:
                    pass
        else:
            element.clear()
    return root_attributes, books


def first_non_blank(attributes: dict[str, str], names: tuple[str, ...]) -> str:
    for name in names:
        value = (attributes.get(name) or "").strip()
        if value:
            return value
    return ""


def entry_for(path: Path, blob: str, size: int, languages: Languages) -> dict:
    stem = path.name[: -len("Bible.xml")] if path.name.endswith("Bible.xml") else path.stem
    tokens = tokenize(stem)
    code, language_name, provenance, consumed = assign_language(stem, tokens, languages)

    attributes, books = read_xml(path)
    title = first_non_blank(attributes, TITLE_ATTRIBUTES)

    # Several files put the language name in the title attribute and nothing else, which would make
    # for a browse list of a hundred rows all called "Spanish". Where that happens the file name is
    # more informative, because its trailing tokens are the edition: `Spanish1909` -> "Spanish 1909".
    if not title or languages.lookup(title) is not None:
        title = " ".join(tokens)

    return {
        "file": path.name,
        "sha": blob,
        "size": size,
        "title": title,
        "id": "".join(c for c in "".join(tokens[consumed:]).upper() if c.isalnum()),
        "lang": code,
        "langName": language_name,
        "langFrom": provenance,
        "rights": first_non_blank(attributes, RIGHTS_ATTRIBUTES),
        "url": first_non_blank(attributes, SOURCE_ATTRIBUTES),
        "ot": len([b for b in books if b in OT_RANGE]),
        "nt": len([b for b in books if b in NT_RANGE]),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, type=Path, help="local clone of the archive")
    parser.add_argument("--out", type=Path, help="output path (default: <repo>/catalog.json)")
    parser.add_argument("--refresh-languages", action="store_true", help="re-download eBible's catalogue")
    parser.add_argument("--max-unknown", type=int, default=20, help="fail past this many UND rows")
    args = parser.parse_args()

    repo: Path = args.repo.expanduser()
    if not (repo / ".git").exists():
        print(f"error: {repo} is not a git clone", file=sys.stderr)
        return 2

    commit = run(repo, "rev-parse", "HEAD").strip()
    languages = Languages(args.refresh_languages)

    # `ls-tree -rl` gives the blob hash and the exact byte size in one pass — no hashing, no stat.
    listing = []
    for line in run(repo, "ls-tree", "-rl", "HEAD").splitlines():
        meta, name = line.split("\t", 1)
        _mode, kind, blob, size = meta.split()
        if kind == "blob" and name.endswith(".xml") and "/" not in name:
            listing.append((name, blob, int(size)))
    listing.sort()

    print(f"Reading {len(listing)} files at {commit[:10]} …", flush=True)
    entries = []
    for index, (name, blob, size) in enumerate(listing, start=1):
        entries.append(entry_for(repo / name, blob, size, languages))
        if index % 100 == 0:
            print(f"  {index}/{len(listing)}", flush=True)

    out: Path = args.out or (repo / "catalog.json")
    out.write_text(
        json.dumps(
            {"schemaVersion": SCHEMA_VERSION, "commit": commit, "bibles": entries},
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )

    unknown = [e for e in entries if e["lang"] == UNKNOWN_LANGUAGE]
    by_source: dict[str, int] = {}
    for entry in entries:
        by_source[entry["langFrom"]] = by_source.get(entry["langFrom"], 0) + 1

    print(f"\nWrote {out} — {len(entries)} translations, {out.stat().st_size:,} bytes")
    print(f"  languages: {', '.join(f'{k}={v}' for k, v in sorted(by_source.items()))}")
    print(f"  distinct codes: {len({e['lang'] for e in entries})}")
    print(f"  no copyright stated: {len([e for e in entries if not e['rights']])}")
    if unknown:
        print(f"\n  {len(unknown)} unresolved — add these to NAME_OVERRIDES:")
        for entry in unknown:
            print(f"    {entry['file']}")
    if len(unknown) > args.max_unknown:
        print(f"\nerror: {len(unknown)} unresolved exceeds --max-unknown={args.max_unknown}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
