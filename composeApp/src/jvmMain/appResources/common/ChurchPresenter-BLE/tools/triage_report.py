#!/usr/bin/env python3
"""
Minimal triage report from detection-log + live-references. No DB needed.

Anchors on each operator go-live event and classifies what the engine did in the
[-90s, +5s] window around it:

  TP        — engine fired the correct ref in the window
  PREMATURE — engine fired the right book+chapter but wrong verse first, then corrected
  LATE      — engine named the ref correctly, but only after the operator had gone live
              (inside ±window-before, past the +window-after cut) — a latency problem
  FN        — engine missed the ref entirely (or fired the wrong book/chapter only)
  FP        — wrong engine detections inside a live-ref window

Recall counts LATE against the engine (it did not beat the operator); Coverage does not
(it did eventually name the verse). Read them as a pair: Coverage ≫ Recall means the
engine is finding the right verses but trailing the operator, which is a Stabilizer /
STT-finalisation question, not a parsing one.

Refs are compared in canonical numbering whenever the ground truth is canonical — see
`ref_of` — matching ReplayEval.sameVerse.

Everything outside all live-ref windows is ignored, so announcements, prayers, and
pre/post-service noise never inflate the counts.

Usage:
    python tools/triage_report.py \\
        --dlog detection-log-<session>.jsonl \\
        --lref live-references-<session>.jsonl \\
        [--label service6]  [--window-before 90]  [--window-after 5]
"""
import argparse, json, re, sys, io, statistics
from datetime import datetime, timezone

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

WINDOW_BEFORE_S = 90
WINDOW_AFTER_S  = 5

CODE_RE = re.compile(r"^B(\d{3})C(\d{3})V(\d{3})$")


# ── helpers ──────────────────────────────────────────────────────────────────

def parse_code(code):
    """`BxxxCyyyVzzz` → (book, chapter, verse), the numbering every module agrees on."""
    m = CODE_RE.match((code or "").strip())
    return (int(m.group(1)), int(m.group(2)), int(m.group(3))) if m else None

def parse_iso(s):
    if not s:
        return None
    try:
        return datetime.fromisoformat(s.strip().replace("Z", "+00:00")).timestamp() * 1000.0
    except Exception:
        return None

def load_jsonl(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    rows.append(json.loads(line))
                except json.JSONDecodeError:
                    pass
    return rows

def ref_str(book, ch, v, v_end=None):
    s = f"book={book} {ch}:{v}"
    if v_end:
        s += f"-{v_end}"
    return s

def fmt_s(ms):
    if ms is None:
        return "?"
    return f"{ms/1000:+.1f}s"


# ── main logic ────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dlog",  required=True, help="detection-log-*.jsonl path")
    ap.add_argument("--lref",  required=True, help="live-references-*.jsonl path")
    ap.add_argument("--label", default=None,  help="Session label for the header")
    ap.add_argument("--window-before", type=float, default=WINDOW_BEFORE_S,
                    help="Seconds before live-ref to look back (default 90)")
    ap.add_argument("--window-after",  type=float, default=WINDOW_AFTER_S,
                    help="Seconds after live-ref to look forward (default 5)")
    args = ap.parse_args()

    wb_ms = args.window_before * 1000
    wa_ms = args.window_after  * 1000

    label = args.label or args.lref.split("/")[-1].replace("live-references-", "").replace(".jsonl", "")

    raw_dets  = [d for d in load_jsonl(args.dlog) if d.get("type") != "session"]
    raw_lives = [l for l in load_jsonl(args.lref) if l.get("type") != "session"]

    # Build detections with parsed timestamps
    dets = []
    for d in raw_dets:
        ts = parse_iso(d.get("ts"))
        if ts is None:
            continue
        dets.append({
            "ts": ts,
            "book": d.get("book"), "ch": d.get("chapter"), "v": d.get("verseStart"),
            "canon": parse_code(d.get("canonicalStart")),
            "tier": d.get("tier"), "src": d.get("source"),
            "transcript": (d.get("transcript") or "").strip(),
        })

    # Sort live-refs by timestamp
    lives = []
    for l in raw_lives:
        ts = float(l.get("ts_ms") or 0)
        if ts == 0:
            continue
        lives.append({
            "ts": ts,
            "book": l.get("book"), "ch": l.get("chapter"), "v": l.get("verseStart"),
            # Canonical rows are recognised by the display* fields ChurchPresenter writes
            # alongside the canonical ones. See ReplayEval.GroundTruth.
            "canonical": "displayChapter" in l,
            "auto": l.get("autoFollow", False),
        })
    lives.sort(key=lambda x: x["ts"])

    n_canonical = sum(1 for l in lives if l["canonical"])

    if not lives:
        print("No live-reference events found.")
        return

    tps, fns, fps, prematures, lates = [], [], [], [], []
    latencies = []

    def ref_of(live, det):
        """
        The detection's ref in whichever numbering `live` is expressed in.

        The engine reports the matched *module's* numbering in book/chapter/verseStart and the
        module-independent one in canonicalStart. Comparing a canonical ground truth against the
        display fields scores every Psalm as a simultaneous FN and FP — Synodal Ps 61:13 is
        canonical Ps 62:12 — which is what this tool used to do. Mirrors
        ReplayEval.sameVerse; the two must agree or the same session scores differently
        depending on which one you run.
        """
        if live["canonical"] and det["canon"] is not None:
            return det["canon"]
        return (det["book"], det["ch"], det["v"])

    for live in lives:
        lt = live["ts"]
        b, c, v = live["book"], live["ch"], live["v"]

        # Detections in the window around this go-live event
        window = [d for d in dets if lt - wb_ms <= d["ts"] <= lt + wa_ms]

        # Split into: correct (exact match), same-chapter (potential premature), other (FP)
        correct = [d for d in window if ref_of(live, d) == (b, c, v)]
        same_ch = [d for d in window if ref_of(live, d)[:2] == (b, c) and ref_of(live, d)[2] != v]
        wrong   = [d for d in window if ref_of(live, d)[:2] != (b, c)]

        if correct:
            # Find the earliest correct detection
            earliest_correct = min(correct, key=lambda d: d["ts"])
            lat = earliest_correct["ts"] - lt  # negative = engine ahead of operator
            latencies.append(lat)

            # Premature = a same-chapter wrong-verse detection that fired BEFORE the correct one
            pre = [d for d in same_ch if d["ts"] < earliest_correct["ts"]]
            if pre:
                earliest_pre = min(pre, key=lambda d: d["ts"])
                prematures.append({
                    "live": live, "correct": earliest_correct, "premature": earliest_pre,
                    "lat_correct": lat,
                    "lat_premature": earliest_pre["ts"] - lt,
                })
            else:
                tps.append({"live": live, "det": earliest_correct, "lat": lat})

            # FPs = wrong-book/chapter detections in window (engine was also noisy alongside correct)
            for d in wrong:
                fps.append({"live_ref": ref_str(b, c, v), "det": d, "ref": ref_of(live, d)})
        else:
            # No detection of this ref inside [-before, +after]. Before calling it a miss, look
            # for one in the symmetric ±before window: the engine routinely confirms a verse a
            # few seconds AFTER the operator has already clicked it (median latency runs from
            # -4s to +5s, tail to +12s). That is a latency problem, not a recall one, and
            # scoring it as FN *and* FP at once is what made recorded sessions read as ~47%
            # recall when the engine had in fact named 85% of the operator's verses.
            late = [d for d in dets
                    if lt + wa_ms < d["ts"] <= lt + wb_ms and ref_of(live, d) == (b, c, v)]
            if late:
                earliest_late = min(late, key=lambda d: d["ts"])
                lates.append({"live": live, "det": earliest_late,
                              "lat": earliest_late["ts"] - lt})
            else:
                fns.append({"live": live, "wrong_in_window": wrong + same_ch})
            # All detections in window (wrong book, wrong verse) are FPs
            for d in wrong + same_ch:
                fps.append({"live_ref": ref_str(b, c, v), "det": d, "ref": ref_of(live, d)})

    # ── output ────────────────────────────────────────────────────────────────

    total_events = len(lives)
    n_tp = len(tps) + len(prematures)  # premature = late-corrected TP
    n_late = len(lates)
    n_fp = len(fps)
    n_fn = len(fns)
    n_pre = len(prematures)
    precision = n_tp / (n_tp + n_fp) if (n_tp + n_fp) else None
    recall    = n_tp / (n_tp + n_fn + n_late) if (n_tp + n_fn + n_late) else None
    # Named-at-all: TP + the ones the engine got right but only after the operator had clicked.
    coverage  = (n_tp + n_late) / total_events if total_events else None
    med_lat   = statistics.median(latencies) if latencies else None

    def pct(v):
        return f"{v:.1%}" if v is not None else "—"

    print()
    print(f"=== {label}  events={total_events}  "
          f"Precision={pct(precision)}  Recall={pct(recall)}  "
          f"Premature={n_pre}  Late={n_late}  Coverage={pct(coverage)}  "
          f"Med.latency={fmt_s(med_lat)} ===")
    if n_canonical == total_events:
        print("    refs compared in canonical numbering")
    elif n_canonical == 0:
        print("    refs compared in the primary Bible's own numbering "
              "(legacy recording — no display* fields)")
    else:
        print(f"    MIXED numbering: {n_canonical}/{total_events} live refs canonical — "
              "check the recording, this should not happen within one session")
    print()

    if lates:
        print(f"LATE ({len(lates)} — engine named the verse, but after the operator went live):")
        for la in lates:
            l = la["live"]
            print(f"  {ref_str(l['book'], l['ch'], l['v'])}  at {fmt_s(la['lat'])}")
        print()

    if fns:
        print(f"MISSED ({len(fns)} FN — no detection of this ref within ±{args.window_before:.0f}s):")
        for fn in fns:
            l = fn["live"]
            wrong = fn["wrong_in_window"]
            note = ""
            if wrong:
                ex = wrong[0]
                note = f" — engine fired {ref_str(*ref_of(l, ex))} instead"
                if ex["transcript"]:
                    note += f'  trigger: "{ex["transcript"][:80]}"'
            print(f"  {ref_str(l['book'], l['ch'], l['v'])}{note}")
        print()

    if prematures:
        print(f"PREMATURE ({len(prematures)}):")
        for p in prematures:
            l, pre, cor = p["live"], p["premature"], p["correct"]
            pre_lat = fmt_s(p["lat_premature"])
            cor_lat = fmt_s(p["lat_correct"])
            print(f"  {ref_str(l['book'], l['ch'], l['v'])}  "
                  f"— fired v={ref_of(l, pre)[2]} at {pre_lat}, corrected at {cor_lat}")
        print()

    if fps:
        # Deduplicate FPs by (book, ch, v) to keep output short
        seen = set()
        unique_fps = []
        for fp in fps:
            key = fp["ref"]
            if key not in seen:
                seen.add(key)
                unique_fps.append(fp)
        print(f"WRONG ({len(fps)} FP, {len(unique_fps)} unique refs):")
        for fp in unique_fps[:20]:
            d = fp["det"]
            trigger = f'  trigger: "{d["transcript"][:80]}"' if d["transcript"] else ""
            print(f"  {ref_str(*fp['ref'])}  src={d['src']} tier={d['tier']}{trigger}")
        if len(unique_fps) > 20:
            print(f"  … and {len(unique_fps) - 20} more")
        print()

    if not fns and not prematures and not fps and not lates:
        print("All live-reference events detected correctly with no FPs or premature detections.")
        print()

    if latencies:
        early = sum(1 for l in latencies if l < 0)
        late  = sum(1 for l in latencies if l >= 0)
        print(f"Latency: {early} early (engine ahead of operator)  {late} late/same  "
              f"med={fmt_s(med_lat)}")
        print("  (negative = engine detected before operator went live — good)")
        print()


if __name__ == "__main__":
    main()
