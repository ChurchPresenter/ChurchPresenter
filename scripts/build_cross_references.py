#!/usr/bin/env python3
"""Convert the CrossWire SWORD ``TSK`` module into the app's cross-reference JSON.

The Treasury of Scripture Knowledge (R.A. Torrey and others, c. 1880) is public domain;
CrossWire distributes it as a compressed-commentary (``zCom``) SWORD module, which is what
this reads. Run it once by hand and commit the output — there is no submodule to track and
nothing here belongs in the Gradle build.

    python3 scripts/build_cross_references.py            # downloads the module
    python3 scripts/build_cross_references.py --zip /path/to/TSK.zip

Output: ``../composeApp/src/jvmMain/composeResources/files/bible/cross_references.json``

    {"v":1,"r":{"001001001":"043001001 058011003 019033006-009", ...}}

Keys and targets are packed ``BBBCCCVVV`` strings in canonical numbering (1-39 OT, 40-66 NT),
the same key shape ``InterlinearRepository`` already uses. A target may carry an intra-chapter
range suffix (``-VVV``); ranges that span a chapter boundary are truncated to their start verse,
because the schema cannot express them and the panel's label has to stay short either way.

## The zCom format, since it is undocumented anywhere convenient

Three files per testament (``ot.*``, ``nt.*``):

* ``.bzv`` — one 10-byte record per index slot: block number (uint32), offset within the
  decompressed block (uint32), length (uint16). A zero length means "no note here".
* ``.bzs`` — one 12-byte record per block: offset into ``.bzz`` (uint32), compressed size
  (uint32), decompressed size (uint32).
* ``.bzz`` — the zlib streams themselves.

Slots run in KJV versification order, which is why ``KJV_VERSES`` below has to be exact:

    [0] unused  [1] testament heading  then per book: [book heading]
    then per chapter: [chapter heading] followed by that chapter's verses

``verify_layout`` asserts the resulting slot count against the real file size, so a wrong
verse count is a hard failure here rather than a silent one-verse-off shift through Revelation.
"""

from __future__ import annotations

import argparse
import io
import json
import re
import struct
import sys
import urllib.request
import zipfile
import zlib
from pathlib import Path

TSK_URL = "https://crosswire.org/ftpmirror/pub/sword/packages/rawzip/TSK.zip"

OUT_PATH = (Path(__file__).parent.parent
            / "composeApp/src/jvmMain/composeResources/files/bible/cross_references.json")

# --- KJV versification -------------------------------------------------------------------
# Verse counts per chapter, in canonical order. Checked against the module's own index size.
KJV_VERSES: list[list[int]] = [
    [31,25,24,26,32,22,24,22,29,32,32,20,18,24,21,16,27,33,38,18,34,24,20,67,34,35,46,22,35,43,55,32,20,31,29,43,36,30,23,23,57,38,34,34,28,34,31,22,33,26],
    [22,25,22,31,23,30,25,32,35,29,10,51,22,31,27,36,16,27,25,26,36,31,33,18,40,37,21,43,46,38,18,35,23,35,35,38,29,31,43,38],
    [17,16,17,35,19,30,38,36,24,20,47,8,59,57,33,34,16,30,37,27,24,33,44,23,55,46,34],
    [54,34,51,49,31,27,89,26,23,36,35,16,33,45,41,50,13,32,22,29,35,41,30,25,18,65,23,31,40,16,54,42,56,29,34,13],
    [46,37,29,49,33,25,26,20,29,22,32,32,18,29,23,22,20,22,21,20,23,30,25,22,19,19,26,68,29,20,30,52,29,12],
    [18,24,17,24,15,27,26,35,27,43,23,24,33,15,63,10,18,28,51,9,45,34,16,33],
    [36,23,31,24,31,40,25,35,57,18,40,15,25,20,20,31,13,31,30,48,25],
    [22,23,18,22],
    [28,36,21,22,12,21,17,22,27,27,15,25,23,52,35,23,58,30,24,42,15,23,29,22,44,25,12,25,11,31,13],
    [27,32,39,12,25,23,29,18,13,19,27,31,39,33,37,23,29,33,43,26,22,51,39,25],
    [53,46,28,34,18,38,51,66,28,29,43,33,34,31,34,34,24,46,21,43,29,53],
    [18,25,27,44,27,33,20,29,37,36,21,21,25,29,38,20,41,37,37,21,26,20,37,20,30],
    [54,55,24,43,26,81,40,40,44,14,47,40,14,17,29,43,27,17,19,8,30,19,32,31,31,32,34,21,30],
    [17,18,17,22,14,42,22,18,31,19,23,16,22,15,19,14,19,34,11,37,20,12,21,27,28,23,9,27,36,27,21,33,25,33,27,23],
    [11,70,13,24,17,22,28,36,15,44],
    [11,20,32,23,19,19,73,18,38,39,36,47,31],
    [22,23,15,17,14,14,10,17,32,3],
    [22,13,26,21,27,30,21,22,35,22,20,25,28,22,35,22,16,21,29,29,34,30,17,25,6,14,23,28,25,31,40,22,33,37,16,33,24,41,30,24,34,17],
    [6,12,8,8,12,10,17,9,20,18,7,8,6,7,5,11,15,50,14,9,13,31,6,10,22,12,14,9,11,12,24,11,22,22,28,12,40,22,13,17,13,11,5,26,17,11,9,14,20,23,19,9,6,7,23,13,11,11,17,12,8,12,11,10,13,20,7,35,36,5,24,20,28,23,10,12,20,72,13,19,16,8,18,12,13,17,7,18,52,17,16,15,5,23,11,13,12,9,9,5,8,28,22,35,45,48,43,13,31,7,10,10,9,8,18,19,2,29,176,7,8,9,4,8,5,6,5,6,8,8,3,18,3,3,21,26,9,8,24,13,10,7,12,15,21,10,20,14,9,6],
    [33,22,35,27,23,35,27,36,18,32,31,28,25,35,33,33,28,24,29,30,31,29,35,34,28,28,27,28,27,33,31],
    [18,26,22,16,20,12,29,17,18,20,10,14],
    [17,17,11,16,16,13,13,14],
    [31,22,26,6,30,13,25,22,21,34,16,6,22,32,9,14,14,7,25,6,17,25,18,23,12,21,13,29,24,33,9,20,24,17,10,22,38,22,8,31,29,25,28,28,25,13,15,22,26,11,23,15,12,17,13,12,21,14,21,22,11,12,19,12,25,24],
    [19,37,25,31,31,30,34,22,26,25,23,17,27,22,21,21,27,23,15,18,14,30,40,10,38,24,22,17,32,24,40,44,26,22,19,32,21,28,18,16,18,22,13,30,5,28,7,47,39,46,64,34],
    [22,22,66,22,22],
    [28,10,27,17,17,14,27,18,11,22,25,28,23,23,8,63,24,32,14,49,32,31,49,27,17,21,36,26,21,26,18,32,33,31,15,38,28,23,29,49,26,20,27,31,25,24,23,35],
    [21,49,30,37,31,28,28,27,27,21,45,13],
    [11,23,5,19,15,11,16,14,17,15,12,14,16,9],
    [20,32,21],
    [15,16,15,13,27,14,17,14,15],
    [21],
    [17,10,10,11],
    [16,13,12,13,15,16,20],
    [15,13,19],
    [17,20,19],
    [18,15,20],
    [15,23],
    [21,13,10,14,11,15,14,23,17,12,17,14,9,21],
    [14,17,18,6],
    [25,23,17,25,48,34,29,34,38,42,30,50,58,36,39,28,27,35,30,34,46,46,39,51,46,75,66,20],
    [45,28,35,41,43,56,37,38,50,52,33,44,37,72,47,20],
    [80,52,38,44,39,49,50,56,62,42,54,59,35,35,32,31,37,43,48,47,38,71,56,53],
    [51,25,36,54,47,71,53,59,41,42,57,50,38,31,27,33,26,40,42,31,25],
    [26,47,26,37,42,15,60,40,43,48,30,25,52,28,41,40,34,28,41,38,40,30,35,27,27,32,44,31],
    [32,29,31,25,21,23,25,39,33,21,36,21,14,23,33,27],
    [31,16,23,21,13,20,40,13,27,33,34,31,13,40,58,24],
    [24,17,18,18,21,18,16,24,15,18,33,21,14],
    [24,21,29,31,26,18],
    [23,22,21,32,33,24],
    [30,30,21,23],
    [29,23,25,18],
    [10,20,13,18,28],
    [12,17,18],
    [20,15,16,16,25,21],
    [18,26,17,22],
    [16,15,15],
    [25],
    [14,18,19,16,14,20,28,13,28,39,40,29,25],
    [27,26,18,17,20],
    [25,25,22,19,14],
    [21,22,18],
    [10,29,24,21,21],
    [13],
    [14],
    [25],
    [20,29,22,11,14,17,17,13,21,11,19,17,18,20,8,21,18,24,21,15,27,21],
]

# --- book abbreviations ------------------------------------------------------------------
# TSK's reference text uses the abbreviation set the 19th-century printing used; several are
# ambiguous prefixes of one another ("Jud" is Judges, "Jude" is Jude), so this is a literal
# lookup rather than a prefix match. Keys are lowercased with spaces and dots stripped.
_BOOK_NAMES: list[tuple[int, str]] = [
    (1, "genesis ge gen gn"), (2, "exodus ex exo exod"), (3, "leviticus le lev lv"),
    (4, "numbers nu num nm nb"), (5, "deuteronomy de deut dt deu"),
    (6, "joshua jos josh jsh"), (7, "judges jud judg jdg jg"), (8, "ruth ru rth"),
    (9, "1samuel 1sa 1sam 1s"), (10, "2samuel 2sa 2sam 2s"),
    (11, "1kings 1ki 1kin 1kg 1k"), (12, "2kings 2ki 2kin 2kg 2k"),
    (13, "1chronicles 1ch 1chr 1chron"), (14, "2chronicles 2ch 2chr 2chron"),
    (15, "ezra ezr"), (16, "nehemiah ne neh"), (17, "esther es est esth"),
    (18, "job jb"), (19, "psalms psalm ps psa psm pss"),
    (20, "proverbs pr pro prov prv"), (21, "ecclesiastes ec ecc eccl"),
    (22, "songofsolomon song ss sos can cant canticles"),
    (23, "isaiah isa is"), (24, "jeremiah jer je jr"), (25, "lamentations la lam"),
    (26, "ezekiel eze ezek ezk"), (27, "daniel da dan dn"),
    (28, "hosea ho hos"), (29, "joel joe jl"), (30, "amos am amo"),
    (31, "obadiah ob oba obad"), (32, "jonah jon jnh"), (33, "micah mic mi"),
    (34, "nahum na nah"), (35, "habakkuk hab hb"), (36, "zephaniah zep zeph zph"),
    (37, "haggai hag hg"), (38, "zechariah zec zech zch"), (39, "malachi mal ml"),
    (40, "matthew mt mat matt"), (41, "mark mr mk mar"), (42, "luke lu lk luk"),
    (43, "john joh jn jhn"), (44, "acts ac act"), (45, "romans ro rom rm"),
    (46, "1corinthians 1co 1cor"), (47, "2corinthians 2co 2cor"),
    (48, "galatians ga gal"), (49, "ephesians eph ep"),
    (50, "philippians php phi phil"), (51, "colossians col cl"),
    (52, "1thessalonians 1th 1thes 1thess"), (53, "2thessalonians 2th 2thes 2thess"),
    (54, "1timothy 1ti 1tim"), (55, "2timothy 2ti 2tim"), (56, "titus tit ti"),
    (57, "philemon phm phile philem pm"), (58, "hebrews heb hbr"),
    (59, "james jas jam jm"), (60, "1peter 1pe 1pet 1pt"), (61, "2peter 2pe 2pet 2pt"),
    (62, "1john 1jo 1joh 1jn"), (63, "2john 2jo 2joh 2jn"), (64, "3john 3jo 3joh 3jn"),
    (65, "jude jde jud1"), (66, "revelation re rev rv"),
]

BOOKS: dict[str, int] = {}
for _num, _aliases in _BOOK_NAMES:
    for _alias in _aliases.split():
        BOOKS[_alias] = _num

MAX_TARGETS_PER_VERSE = 16

SCRIP_REF = re.compile(r"<scripRef(?:\s[^>]*)?>(.*?)</scripRef>", re.S)
# "Ge 21:2-5", "1Ch 1:28", "Ps 119", "Jude 6" — book name, then the numeric tail.
BOOK_HEAD = re.compile(r"^((?:[123]\s*)?[A-Za-z][A-Za-z.]*)\s*(.*)$", re.S)


def single_chapter(book: int) -> bool:
    return len(KJV_VERSES[book - 1]) == 1


def pack(book: int, chapter: int, verse: int) -> str:
    return f"{book:03d}{chapter:03d}{verse:03d}"


def in_range(book: int, chapter: int, verse: int) -> bool:
    """True when the reference exists in the KJV. TSK's printed refs contain typos."""
    if not 1 <= book <= 66:
        return False
    chapters = KJV_VERSES[book - 1]
    if not 1 <= chapter <= len(chapters):
        return False
    return 1 <= verse <= chapters[chapter - 1]


# --- module reading ----------------------------------------------------------------------

def slot_layout(testament: str) -> list[tuple[int, int, int] | None]:
    """Index slot -> (book, chapter, verse), with None for the heading slots."""
    books = range(1, 40) if testament == "ot" else range(40, 67)
    slots: list[tuple[int, int, int] | None] = [None, None]  # unused + testament heading
    for book in books:
        slots.append(None)  # book heading
        for chapter, verses in enumerate(KJV_VERSES[book - 1], start=1):
            slots.append(None)  # chapter heading
            slots.extend((book, chapter, verse) for verse in range(1, verses + 1))
    return slots


def verify_layout(testament: str, bzv: bytes) -> list[tuple[int, int, int] | None]:
    slots = slot_layout(testament)
    actual = len(bzv) // 10
    if len(slots) != actual:
        raise SystemExit(
            f"{testament}: versification mismatch — KJV_VERSES implies {len(slots)} index "
            f"slots but {testament}.bzv holds {actual}. The verse-count table is wrong."
        )
    return slots


def read_notes(files: dict[str, bytes], testament: str) -> dict[tuple[int, int, int], str]:
    bzv, bzs, bzz = files[f"{testament}.bzv"], files[f"{testament}.bzs"], files[f"{testament}.bzz"]
    slots = verify_layout(testament, bzv)

    blocks: dict[int, bytes] = {}

    def block(n: int) -> bytes:
        if n not in blocks:
            offset, compressed, _ = struct.unpack_from("<III", bzs, n * 12)
            blocks[n] = zlib.decompress(bzz[offset:offset + compressed])
        return blocks[n]

    notes: dict[tuple[int, int, int], str] = {}
    for index, ref in enumerate(slots):
        if ref is None:
            continue
        number, start, length = struct.unpack_from("<IIH", bzv, index * 10)
        if length:
            notes[ref] = block(number)[start:start + length].decode("utf-8", "replace")
    return notes


# --- reference parsing -------------------------------------------------------------------

def parse_refs(note: str, source: tuple[int, int, int]) -> list[str]:
    """Every scripture reference in one verse's TSK note, in printed order, deduped.

    TSK writes runs like ``Ge 38:27,29,30; 46:12; 1Ch 2:3,4`` where each piece inherits what
    the previous one established: a bare ``29`` is another verse of the same chapter, a bare
    ``46:12`` is another chapter of the same book. Pieces that are prose rather than a
    reference (``* Judah, Pharez, Zarah:``) simply fail to parse and are dropped.
    """
    out: list[str] = []
    seen: set[str] = set()

    for body in SCRIP_REF.findall(note):
        book = chapter = None
        for piece in body.split(";"):
            piece = re.sub(r"<[^>]+>", " ", piece).strip().strip(".,")
            if not piece or "*" in piece:
                continue

            head = BOOK_HEAD.match(piece)
            if head and re.search(r"[A-Za-z]", head.group(1)):
                key = head.group(1).lower().replace(".", "").replace(" ", "")
                if key not in BOOKS:
                    continue  # prose, or an abbreviation this table does not know
                book = BOOKS[key]
                chapter = 1 if single_chapter(book) else None
                piece = head.group(2).strip()
            if book is None:
                continue

            for ref in parse_numbers(piece, book, chapter, source, seen, out):
                chapter = ref  # a piece may move the chapter on for the pieces after it
    return out


def parse_numbers(piece: str, book: int, chapter: int | None, source: tuple[int, int, int],
                  seen: set[str], out: list[str]):
    """Consume the numeric tail of one piece, appending packed refs to ``out``.

    Yields the chapter each sub-piece ended on, so the caller can carry it forward.
    """
    if not piece:
        # A whole-book reference ("see Obadiah") — nothing precise enough to link to.
        return
    for part in piece.split(","):
        part = part.strip()
        if not part:
            continue

        # "2:3-4:5" (spans chapters), "2:3-5", "2:3", "3-5", "3"
        match = re.match(r"^(\d+):(\d+)\s*-\s*(\d+):(\d+)$", part)
        if match:
            chapter, start = int(match.group(1)), int(match.group(2))
            # Cross-chapter: keep the start verse only — see the module docstring.
            emit(book, chapter, start, None, source, seen, out)
            yield chapter
            continue

        match = re.match(r"^(\d+):(\d+)\s*-\s*(\d+)$", part)
        if match:
            chapter, start, end = int(match.group(1)), int(match.group(2)), int(match.group(3))
            emit(book, chapter, start, end, source, seen, out)
            yield chapter
            continue

        match = re.match(r"^(\d+):(\d+)$", part)
        if match:
            chapter, verse = int(match.group(1)), int(match.group(2))
            emit(book, chapter, verse, None, source, seen, out)
            yield chapter
            continue

        match = re.match(r"^(\d+)\s*-\s*(\d+)$", part)
        if match and chapter is not None:
            emit(book, chapter, int(match.group(1)), int(match.group(2)), source, seen, out)
            yield chapter
            continue

        match = re.match(r"^(\d+)$", part)
        if match:
            if chapter is None:
                # "Ps 119" with no verse — a whole chapter. Link to its first verse.
                emit(book, int(match.group(1)), 1, None, source, seen, out)
                yield int(match.group(1))
            else:
                emit(book, chapter, int(match.group(1)), None, source, seen, out)
                yield chapter


def emit(book: int, chapter: int, verse: int, end: int | None,
         source: tuple[int, int, int], seen: set[str], out: list[str]):
    if not in_range(book, chapter, verse):
        return
    if (book, chapter, verse) == source:
        return  # a verse is not a cross-reference to itself
    if end is not None and (end <= verse or not in_range(book, chapter, end)):
        end = None
    packed = pack(book, chapter, verse) + (f"-{end:03d}" if end else "")
    if packed in seen:
        return
    seen.add(packed)
    out.append(packed)


# --- driver ------------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip", type=Path,
                        help=f"a local copy of the module; downloaded from {TSK_URL} if omitted")
    parser.add_argument("--out", type=Path, default=OUT_PATH)
    args = parser.parse_args()

    if args.zip:
        source: object = args.zip
    else:
        print(f"Downloading {TSK_URL} …", flush=True)
        request = urllib.request.Request(
            TSK_URL, headers={"User-Agent": "ChurchPresenter-TskConverter/1.0"})
        with urllib.request.urlopen(request, timeout=120) as response:
            source = io.BytesIO(response.read())

    wanted = ("ot.bzv", "ot.bzs", "ot.bzz", "nt.bzv", "nt.bzs", "nt.bzz")
    files: dict[str, bytes] = {}
    with zipfile.ZipFile(source) as archive:
        for entry in archive.namelist():
            name = entry.rsplit("/", 1)[-1]
            if name in wanted:
                files[name] = archive.read(entry)
    missing = [name for name in wanted if name not in files]
    if missing:
        raise SystemExit(f"missing {', '.join(missing)} — is this really the TSK module?")

    refs: dict[str, str] = {}
    truncated = 0
    for testament in ("ot", "nt"):
        for source, note in read_notes(files, testament).items():
            targets = parse_refs(note, source)
            if not targets:
                continue
            if len(targets) > MAX_TARGETS_PER_VERSE:
                truncated += 1
                targets = targets[:MAX_TARGETS_PER_VERSE]
            refs[pack(*source)] = " ".join(targets)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        json.dumps({"v": 1, "r": dict(sorted(refs.items()))}, separators=(",", ":")),
        encoding="utf-8",
    )

    links = validate(args.out)
    print(f"{len(refs)} verses, {links} links, {truncated} verses truncated "
          f"to {MAX_TARGETS_PER_VERSE}, {args.out.stat().st_size / 1e6:.2f} MB -> {args.out}")
    return 0


def validate(path: Path) -> int:
    """Re-read the written file and check every reference in it.

    This is where the shipped data is verified. The app's own test suite deliberately does not:
    parsing a quarter of a million links would dominate its runtime, and it stubs bundled
    resources rather than reading them. So a regeneration that produces nonsense has to fail
    here, at the point it happens, rather than reaching a service.
    """
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("v") != 1:
        raise SystemExit(f"{path}: unexpected format version {data.get('v')!r}")

    links = 0
    for key, value in data["r"].items():
        source = (int(key[:3]), int(key[3:6]), int(key[6:]))
        if len(key) != 9 or not in_range(*source):
            raise SystemExit(f"{path}: {key} is not a verse")
        if not value:
            raise SystemExit(f"{path}: {key} has an empty target list")
        for target in value.split():
            start, _, end = target.partition("-")
            book, chapter, verse = int(start[:3]), int(start[3:6]), int(start[6:])
            if len(start) != 9 or not in_range(book, chapter, verse):
                raise SystemExit(f"{path}: {key} -> {target} is not a verse")
            if (book, chapter, verse) == source:
                raise SystemExit(f"{path}: {key} references itself")
            if end and (int(end) <= verse or not in_range(book, chapter, int(end))):
                raise SystemExit(f"{path}: {key} -> {target} has an impossible range")
            links += 1
    return links


if __name__ == "__main__":
    sys.exit(main())
