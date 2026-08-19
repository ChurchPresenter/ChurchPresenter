#!/usr/bin/env python3
"""
Extract training samples from a service session triple (DB, detection-log, live-references).

Anchors on each operator go-live event (ground truth) and classifies what the engine did
in the [-90s, +5s] window around it. This naturally ignores announcement/prayer/music FPs
because only the sermon time (bracketed by live-references) is analyzed.

Event types:
  tp        — engine detected the correct ref in the window
  premature — engine fired the right book+chapter but wrong verse first, then corrected
  fn        — engine missed the ref (nothing correct in window)
  fp        — wrong engine detections inside a live-ref window (one record per wrong detection)

Default output: compact (one line per event, anchor_text only — no full context windows).
Add --windows to include 15-row context windows for offline deep-dives.

Usage:
    python tools/extract_training_samples.py \\
        --db    /path/to/service.db \\
        --dlog  /path/to/detection-log-*.jsonl \\
        --lref  /path/to/live-references-*.jsonl \\
        --out   training-samples-serviceN.jsonl \\
        [--label serviceN]  [--windows]
"""
import argparse, json, os, sqlite3, statistics, sys, io
from datetime import datetime, timezone

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

WINDOW_BEFORE_MS = 90_000   # look back 90 s from go-live
WINDOW_AFTER_MS  =  5_000   # look forward 5 s
CONTEXT_BEFORE   = 12       # rows before anchor (--windows mode)
CONTEXT_AFTER    =  2       # rows after anchor  (--windows mode)


# ── helpers ──────────────────────────────────────────────────────────────────

def parse_iso(s):
    if not s:
        return None
    try:
        return datetime.fromisoformat(s.strip().replace("Z", "+00:00")).timestamp() * 1000.0
    except Exception:
        return None

def parse_db_ts(s):
    if not s:
        return None
    try:
        return datetime.strptime(s.strip(), "%Y-%m-%d %H:%M:%S").replace(
            tzinfo=timezone.utc
        ).timestamp() * 1000.0
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

def load_db(path):
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    rows = list(conn.execute(
        "SELECT id, timestamp, text, translated_text, speech_type "
        "FROM transcriptions ORDER BY id"
    ))
    conn.close()
    return rows

def build_ts_index(rows):
    idx = []
    for r in rows:
        ts = parse_db_ts(r["timestamp"])
        if ts is not None:
            idx.append((ts, r["id"]))
    return sorted(idx)

def nearest_row(ts_ms, ts_index, rows_by_id):
    if not ts_index:
        return None, None
    lo, hi = 0, len(ts_index) - 1
    best_id, best_diff = ts_index[0][1], abs(ts_index[0][0] - ts_ms)
    while lo <= hi:
        mid = (lo + hi) // 2
        diff = abs(ts_index[mid][0] - ts_ms)
        if diff < best_diff:
            best_diff, best_id = diff, ts_index[mid][1]
        if ts_index[mid][0] < ts_ms:
            lo = mid + 1
        else:
            hi = mid - 1
    row = rows_by_id.get(best_id)
    return best_id, row

def extract_window(rows_by_id, anchor_id, sermon_ids):
    sermon_set = set(sermon_ids)
    if anchor_id not in sermon_set:
        if not sermon_ids:
            return []
        anchor_id = min(sermon_ids, key=lambda x: abs(x - anchor_id))
    idx = sermon_ids.index(anchor_id)
    lo = max(0, idx - CONTEXT_BEFORE)
    hi = min(len(sermon_ids), idx + CONTEXT_AFTER + 1)
    return [
        {"id": sermon_ids[k],
         "text": rows_by_id[sermon_ids[k]]["text"],
         "translated": rows_by_id[sermon_ids[k]]["translated_text"]}
        for k in range(lo, hi)
        if sermon_ids[k] in rows_by_id
    ]


# ── main ─────────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--db",      required=True, help="Path to service .db file")
    ap.add_argument("--dlog",    required=True, help="Path to detection-log-*.jsonl")
    ap.add_argument("--lref",    required=True, help="Path to live-references-*.jsonl")
    ap.add_argument("--out",     required=True, help="Output path for training-samples-*.jsonl")
    ap.add_argument("--label",   default="",   help="Session label (defaults to db filename stem)")
    ap.add_argument("--windows", action="store_true",
                    help="Include full 15-row context windows (default: anchor row only)")
    args = ap.parse_args()

    label = args.label or os.path.basename(args.db).replace(".db", "")

    print(f"Loading DB: {args.db}")
    db_rows = load_db(args.db)
    rows_by_id = {r["id"]: r for r in db_rows}
    sermon_ids = sorted(
        r["id"] for r in db_rows
        if not (r["speech_type"] or "").lower().startswith("music")
    )
    ts_index = build_ts_index(db_rows)
    print(f"  {len(db_rows)} total rows, {len(sermon_ids)} non-Music rows")

    print(f"Loading detection-log: {args.dlog}")
    raw_dets = [d for d in load_jsonl(args.dlog) if d.get("type") != "session"]
    dets = []
    for d in raw_dets:
        ts = parse_iso(d.get("ts"))
        if ts is None:
            continue
        dets.append({
            "ts": ts,
            "book": d.get("book"), "ch": d.get("chapter"), "v": d.get("verseStart"),
            "tier": d.get("tier"), "src": d.get("source"),
            "transcript": (d.get("transcript") or "").strip(),
        })
    print(f"  {len(dets)} detections with timestamps")

    print(f"Loading live-references: {args.lref}")
    raw_lives = [l for l in load_jsonl(args.lref) if l.get("type") != "session"]
    lives = []
    for l in raw_lives:
        ts = float(l.get("ts_ms") or 0)
        if ts == 0:
            continue
        lives.append({
            "ts": ts,
            "book": l.get("book"), "ch": l.get("chapter"), "v": l.get("verseStart"),
        })
    lives.sort(key=lambda x: x["ts"])
    print(f"  {len(lives)} live-reference events")

    out_samples = []
    latencies = []

    for live in lives:
        lt   = live["ts"]
        b, c, v = live["book"], live["ch"], live["v"]
        expected = {"book": b, "ch": c, "v": v}

        window_dets = [d for d in dets if lt - WINDOW_BEFORE_MS <= d["ts"] <= lt + WINDOW_AFTER_MS]
        correct = [d for d in window_dets if d["book"] == b and d["ch"] == c and d["v"] == v]
        same_ch = [d for d in window_dets if d["book"] == b and d["ch"] == c and d["v"] != v]
        wrong   = [d for d in window_dets if not (d["book"] == b and d["ch"] == c)]

        anchor_id, anchor_row = nearest_row(lt, ts_index, rows_by_id)
        anchor_text       = (anchor_row["text"]            if anchor_row else "") or ""
        anchor_translated = (anchor_row["translated_text"] if anchor_row else "") or ""

        def make_base(event_type, det=None, lat=None):
            rec = {
                "session_id": label, "event_type": event_type,
                "anchor_text": anchor_text.strip(),
                "anchor_translated": anchor_translated.strip(),
                "expected_ref": expected,
                "detected_ref": {
                    "book": det["book"], "ch": det["ch"], "v": det["v"],
                    "tier": det["tier"], "src": det["src"],
                    "transcript": det["transcript"],
                } if det else None,
                "latency_ms": int(lat) if lat is not None else None,
            }
            if args.windows:
                rec["rows"] = extract_window(rows_by_id, anchor_id, sermon_ids) if anchor_id else []
            return rec

        if correct:
            earliest = min(correct, key=lambda d: d["ts"])
            lat = earliest["ts"] - lt
            latencies.append(lat)

            pre = [d for d in same_ch if d["ts"] < earliest["ts"]]
            if pre:
                earliest_pre = min(pre, key=lambda d: d["ts"])
                rec = make_base("premature", det=earliest, lat=lat)
                rec["premature_ref"] = {
                    "book": earliest_pre["book"], "ch": earliest_pre["ch"],
                    "v": earliest_pre["v"], "tier": earliest_pre["tier"],
                    "src": earliest_pre["src"],
                    "transcript": earliest_pre["transcript"],
                    "latency_ms": int(earliest_pre["ts"] - lt),
                }
                out_samples.append(rec)
            else:
                out_samples.append(make_base("tp", det=earliest, lat=lat))

            for d in wrong:
                out_samples.append(make_base("fp", det=d))
        else:
            out_samples.append(make_base("fn"))
            for d in wrong + same_ch:
                out_samples.append(make_base("fp", det=d))

    order = {"fn": 0, "premature": 1, "fp": 2, "tp": 3}
    out_samples.sort(key=lambda x: order.get(x["event_type"], 9))

    with open(args.out, "w", encoding="utf-8") as f:
        for s in out_samples:
            f.write(json.dumps(s, ensure_ascii=False) + "\n")

    tp  = sum(1 for s in out_samples if s["event_type"] == "tp")
    pre = sum(1 for s in out_samples if s["event_type"] == "premature")
    fp  = sum(1 for s in out_samples if s["event_type"] == "fp")
    fn  = sum(1 for s in out_samples if s["event_type"] == "fn")
    prec = (tp + pre) / (tp + pre + fp) if (tp + pre + fp) else 0.0
    rec  = (tp + pre) / (tp + pre + fn) if (tp + pre + fn) else 0.0
    med_lat = f"{statistics.median(latencies)/1000:+.1f}s" if latencies else "n/a"

    print(f"\nWrote {len(out_samples)} samples → {args.out}")
    print(f"  TP={tp}  PREMATURE={pre}  FP={fp}  FN={fn}")
    print(f"  Precision={prec:.1%}  Recall={rec:.1%}  Median latency={med_lat}")
    print("  (negative latency = engine was ahead of operator — good)")


if __name__ == "__main__":
    main()
