#!/usr/bin/env python3
"""
Compute precision / recall / latency metrics from training-samples JSONL files.

Usage:
    # One or more files:
    python tools/eval_metrics.py training-samples-service4.jsonl training-samples-service5.jsonl

    # All files in a directory:
    python tools/eval_metrics.py --dir /path/to/bible-stt-logs-mac-arm
"""
import argparse, json, os, statistics, sys, io
from glob import glob

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")


# ── loading ───────────────────────────────────────────────────────────────────

def load_samples(paths: list[str]) -> list[dict]:
    out = []
    for path in paths:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        out.append(json.loads(line))
                    except json.JSONDecodeError:
                        pass
    return out


# ── metric computation ────────────────────────────────────────────────────────

def compute(samples: list[dict], label: str) -> dict:
    # Skip legacy out-of-scope events (pre-live-ref-centric format)
    active = [s for s in samples if s["event_type"] != "out-of-scope"]
    tp  = [s for s in active if s["event_type"] == "tp"]
    pre = [s for s in active if s["event_type"] == "premature"]
    fp  = [s for s in active if s["event_type"] == "fp"]
    fn  = [s for s in active if s["event_type"] == "fn"]
    n_tp_total = len(tp) + len(pre)   # premature = corrected TP
    prec    = n_tp_total / (n_tp_total + len(fp)) if (n_tp_total or fp) else None
    rec     = n_tp_total / (n_tp_total + len(fn)) if (n_tp_total or fn) else None
    lats    = [s["latency_ms"] for s in tp + pre if s.get("latency_ms") is not None]
    med_lat = statistics.median(lats) if lats else None
    early   = sum(1 for l in lats if l < 0)
    late    = sum(1 for l in lats if l >= 0)
    return {
        "label": label,
        "tp": len(tp), "premature": len(pre), "fp": len(fp), "fn": len(fn),
        "precision": prec, "recall": rec,
        "median_latency_ms": med_lat,
        "engine_early": early,
        "engine_late": late,
    }


# ── formatting ────────────────────────────────────────────────────────────────

def _fmt_pct(v) -> str:
    return f"{v:.1%}" if v is not None else "—"

def _fmt_lat(v) -> str:
    if v is None:
        return "—"
    s = v / 1000.0
    return f"{s:+.1f}s"

def _fmt(v) -> str:
    return "—" if v is None else str(v)


COLS = [
    ("label",              "Session",    lambda r: r["label"]),
    ("tp",                 "TP",         lambda r: _fmt(r["tp"])),
    ("premature",          "Premature",  lambda r: _fmt(r["premature"])),
    ("fp",                 "FP",         lambda r: _fmt(r["fp"])),
    ("fn",                 "FN",         lambda r: _fmt(r["fn"])),
    ("precision",          "Precision",  lambda r: _fmt_pct(r["precision"])),
    ("recall",             "Recall",     lambda r: _fmt_pct(r["recall"])),
    ("median_latency_ms",  "Med.Lat.",   lambda r: _fmt_lat(r["median_latency_ms"])),
    ("engine_early",       "Early",      lambda r: _fmt(r["engine_early"])),
    ("engine_late",        "Late",       lambda r: _fmt(r["engine_late"])),
]


def print_table(rows: list[dict]) -> None:
    headers = [c[1] for c in COLS]
    cells   = [[fn(r) for _, _, fn in COLS] for r in rows]
    widths  = [
        max(len(headers[j]), max((len(cells[i][j]) for i in range(len(cells))), default=0))
        for j in range(len(COLS))
    ]
    sep = "  "

    def row_str(vals):
        return sep.join(v.ljust(widths[j]) for j, v in enumerate(vals))

    print(row_str(headers))
    print(sep.join("-" * w for w in widths))
    for row in cells:
        print(row_str(row))


# ── detail listings ───────────────────────────────────────────────────────────

def _ref_str(ref: dict | None) -> str:
    if not ref:
        return "?"
    b, c, v = ref.get("book"), ref.get("ch"), ref.get("v")
    return f"book={b} ch={c} v={v}"

def _det_str(det: dict | None) -> str:
    if not det:
        return "?"
    b, c, v = det.get("book"), det.get("ch"), det.get("v")
    tier, src = det.get("tier"), det.get("src")
    return f"book={b} ch={c} v={v}  tier={tier} src={src}"


def print_detail(samples: list[dict], event_type: str, heading: str, limit: int = 25) -> None:
    items = [s for s in samples if s["event_type"] == event_type]
    if not items:
        return
    print(f"\n── {heading} ({len(items)}) {'[showing first ' + str(limit) + ']' if len(items) > limit else ''} ──")
    for s in items[:limit]:
        sid = s.get("session_id", "?")
        if event_type == "fn":
            print(f"  [{sid}]  MISSED  {_ref_str(s.get('expected_ref'))}")
            # Show the window context hint (first non-empty row text)
            for row in (s.get("rows") or []):
                txt = (row.get("text") or "").strip()
                if txt:
                    print(f"           context: {txt[:90]}")
                    break
        elif event_type == "fp":
            print(f"  [{sid}]  SPURIOUS  {_det_str(s.get('detected_ref'))}")
            for row in (s.get("rows") or []):
                txt = (row.get("text") or "").strip()
                if txt:
                    print(f"             context: {txt[:90]}")
                    break
        else:  # tp
            lat = s.get("latency_ms")
            lat_str = f"{lat/1000:+.1f}s" if lat is not None else "?"
            print(f"  [{sid}]  OK  {_ref_str(s.get('expected_ref'))}  latency={lat_str}")


# ── main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("files", nargs="*", help="training-samples-*.jsonl files")
    ap.add_argument("--dir", default=None, help="Directory to scan for training-samples-*.jsonl")
    ap.add_argument("--detail", action="store_true", help="Print FN/FP detail listings")
    args = ap.parse_args()

    paths: list[str] = list(args.files)
    if args.dir:
        paths += sorted(glob(os.path.join(args.dir, "training-samples-*.jsonl")))
    if not paths:
        ap.error("No files specified. Pass file paths or use --dir <directory>.")

    all_samples = load_samples(paths)
    if not all_samples:
        print("No samples found.")
        return

    rows: list[dict] = []
    for path in paths:
        samps = load_samples([path])
        if not samps:
            continue
        label = os.path.basename(path).replace("training-samples-", "").replace(".jsonl", "")
        rows.append(compute(samps, label))

    if len(paths) > 1:
        rows.append(compute(all_samples, "TOTAL"))

    print()
    print_table(rows)
    print()
    print("Med.Lat. key: negative = engine detected BEFORE operator (good); positive = late or missed.")

    if args.detail or len(all_samples) <= 50:
        print_detail(all_samples, "fn", "False Negatives — missing detections to fix")
        print_detail(all_samples, "fp", "False Positives — spurious detections to suppress")
        if args.detail:
            print_detail(all_samples, "tp", "True Positives — confirmed detections")
    else:
        fn_count = sum(1 for s in all_samples if s["event_type"] == "fn")
        fp_count = sum(1 for s in all_samples if s["event_type"] == "fp")
        if fn_count or fp_count:
            print(f"\nRun with --detail to see FN ({fn_count}) and FP ({fp_count}) listings.")


if __name__ == "__main__":
    main()
