#!/usr/bin/env python3
"""
Match an STT transcript `.db` to its engine `detection-log` + `live-references` jsonl — robustly,
without trusting filenames or wall clocks.

Why this exists
---------------
The three artifacts of one service are stamped at different moments and on possibly different
machines:
  - `<stamp>.db`                  written by the STT app   (its own clock / session start)
  - `detection-log-<stamp>.jsonl` written by the engine    (DetectionLogger init)
  - `live-references-<stamp>.jsonl` written by ChurchPresenter (TrainingDataLogger init)
In the field STT and ChurchPresenter can start 30+ minutes apart, so the filename stamps do NOT
line up. And `segment_id` restarts at ~1 every session, so it can't tell two services apart either.

So we match on what is invariant and session-unique:
  1. db  <-> detection-log : TRANSCRIPT CONTENT. The engine logs the triggering transcript text;
     each db row's `text` should appear inside one of those (the engine combines the last ~2
     segments + the in-progress one). Clock- and machine-independent. This is the strong key.
  2. detection-log <-> live-references : same process (ChurchPresenter hosts the in-process engine),
     so they share the SAME wall clock and the SAME `segment_id` space — matched by time-window
     overlap + segment-id overlap.
  3. A cross-check: the "STT session epoch" = (row wall-clock) - (session-relative start time),
     constant across a session, computed from both the db (`ts_ms - start_time*1000`) and the
     detection-log (`ts - sttStartTime*1000`). Agreement corroborates the content match (and flags
     inter-machine clock skew when content matches but epochs don't).

If a future `session_id` lands in the headers (see REFERENCE_DETECTION_PLAN §8), this script prefers
it outright and the heuristics become a fallback.

Usage
-----
    python match_training_data.py [ROOT]
ROOT defaults to the current dir; it looks for `*.db` there and `*.jsonl` in ROOT and ROOT/bible-stt-logs.
"""
from __future__ import annotations
import json, os, re, sqlite3, sys, glob
from datetime import datetime, timezone

# ── helpers ────────────────────────────────────────────────────────────────────

_WS = re.compile(r"\s+")
def norm(s: str) -> str:
    """Lowercase + collapse whitespace; drop most punctuation so STT vs logged text compare cleanly."""
    s = (s or "").lower()
    s = re.sub(r"[^0-9a-zа-яё ]+", " ", s)
    return _WS.sub(" ", s).strip()

def iso_ms(ts: str) -> float | None:
    """ISO-8601 (with Z) → epoch ms."""
    if not ts:
        return None
    try:
        return datetime.fromisoformat(ts.replace("Z", "+00:00")).timestamp() * 1000.0
    except ValueError:
        return None

def median(xs):
    xs = sorted(x for x in xs if x is not None)
    return xs[len(xs) // 2] if xs else None

def read_jsonl(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    out.append(json.loads(line))
                except json.JSONDecodeError:
                    pass
    return out

# ── loaders ────────────────────────────────────────────────────────────────────

def load_db(path):
    c = sqlite3.connect(path)
    rows = list(c.execute(
        "SELECT segment_id, text, ts_ms, start_time, session_id "
        "FROM transcriptions" if _has_col(c, "session_id") else
        "SELECT segment_id, text, ts_ms, start_time, NULL FROM transcriptions"))
    c.close()
    texts, segs, epochs, sess = [], set(), [], set()
    for seg, text, ts_ms, start, sid in rows:
        t = norm(text)
        if len(t) >= 12:
            texts.append(t)
        if seg is not None:
            segs.add(str(seg))
        if ts_ms is not None and start is not None:
            epochs.append(ts_ms - start * 1000.0)
        if sid:
            sess.add(str(sid))
    return {"path": path, "name": os.path.basename(path), "texts": texts,
            "segs": segs, "epoch": median(epochs), "n": len(rows), "session_id": sess}

def _has_col(conn, col):
    return any(r[1] == col for r in conn.execute("PRAGMA table_info(transcriptions)"))

def load_detection(path):
    rows = read_jsonl(path)
    header = next((r for r in rows if r.get("type") == "session"), {})
    emis = [r for r in rows if r.get("type") != "session"]
    transcripts = [norm(r.get("transcript", "")) for r in emis]
    segs = {str(r["segmentId"]) for r in emis if r.get("segmentId") not in (None, "null")}
    epochs = []
    walls = []
    for r in emis:
        w = iso_ms(r.get("ts", ""))
        if w is not None:
            walls.append(w)
            st = r.get("sttStartTime")
            if isinstance(st, (int, float)):
                epochs.append(w - st * 1000.0)
    return {"path": path, "name": os.path.basename(path), "transcripts": transcripts,
            "segs": segs, "epoch": median(epochs), "wall": (min(walls), max(walls)) if walls else None,
            "n": len(emis), "session_id": header.get("sessionId") or header.get("sttSessionId")}

def load_walllog(path):  # live-references / candidate-log / suggestion-outcomes (CP wall clock)
    rows = read_jsonl(path)
    body = [r for r in rows if r.get("type") != "session"]
    segs = {str(r["segmentId"]) for r in body if r.get("segmentId") not in (None, "null")}
    walls = [r["ts_ms"] for r in body if isinstance(r.get("ts_ms"), (int, float))]
    if not walls:  # candidate/detection-style rows use ISO `ts`
        walls = [w for w in (iso_ms(r.get("ts", "")) for r in body) if w is not None]
    return {"path": path, "name": os.path.basename(path), "segs": segs,
            "wall": (min(walls), max(walls)) if walls else None, "n": len(body)}

# ── matching ───────────────────────────────────────────────────────────────────

def content_score(db, det):
    """Fraction of the detection-log's transcripts that are grounded in this db.

    Direction matters: every logged detection was *triggered by* a db row, so for the true db nearly
    all detection transcripts contain one of its row texts. (The reverse — db rows inside detections —
    is naturally low, since most db rows never fire a detection.) A detection transcript combines a few
    consecutive db segments, so we count it as grounded when any db row text (>=12 chars) is a
    substring of it.
    """
    if not det["transcripts"]:
        return 0.0
    db_texts = [t for t in db["texts"] if len(t) >= 12]
    if not db_texts:
        return 0.0
    grounded = sum(1 for tr in det["transcripts"] if tr and any(t in tr for t in db_texts))
    return grounded / len(det["transcripts"])

def overlaps(a, b, pad_ms=5 * 60_000):
    if not a or not b:
        return False
    return a[0] - pad_ms <= b[1] and b[0] - pad_ms <= a[1]

def main(root="."):
    dbs = [load_db(p) for p in sorted(glob.glob(os.path.join(root, "*.db")))]
    logdir = os.path.join(root, "bible-stt-logs")
    search = [root, logdir]
    def find(prefix):
        out = []
        for d in search:
            out += glob.glob(os.path.join(d, f"{prefix}*.jsonl"))
        return sorted(set(out))
    dets = [load_detection(p) for p in find("detection-log-")]
    lrefs = [load_walllog(p) for p in find("live-references-")]
    cands = [load_walllog(p) for p in find("candidate-log-")]
    sugg = [load_walllog(p) for p in find("suggestion-outcomes-")]

    if not dbs:
        print("No .db files found under", os.path.abspath(root)); return
    print(f"Found {len(dbs)} db, {len(dets)} detection-log, {len(lrefs)} live-references, "
          f"{len(cands)} candidate-log, {len(sugg)} suggestion-outcomes\n")

    # Max |STT-session-epoch| difference (seconds) for two artifacts to be the SAME session. Generous
    # enough for inter-machine NTP skew, far below the gap between distinct services (usually many min).
    EPOCH_TOL_S = 300

    def epoch_skew(db, det):
        return (abs(db['epoch'] - det['epoch']) / 1000.0
                if db['epoch'] and det['epoch'] else None)

    def belongs(db, det):
        """Is this detection-log a fragment of this db's STT session? Returns (yes, score, skew).

        A CP restart mid-session splits the engine/CP logs into multiple files while the STT db stays
        one continuous file — so a db legitimately matches MANY detection-logs. The two keys, BOTH
        required (content alone can't separate two runs of the same sermon):
          - content: the fragment's detections are grounded in this db (it's this sermon), AND
          - epoch:   it shares this db's STT-session epoch (it's this *instance* of the session) —
                     sttStartTime is relative to the continuous STT session, not the CP process, so a
                     true restart-fragment matches within clock skew while a different service is min+ off.
        """
        if det["session_id"] and det["session_id"] in db["session_id"]:
            return True, 1.0, epoch_skew(db, det)
        s = content_score(db, det)
        sk = epoch_skew(db, det)
        if sk is not None:
            return (sk <= EPOCH_TOL_S and s >= 0.15), s, sk   # epoch is the session-identity key
        return s >= 0.7, s, sk                                # no epoch data → strong content only

    used_det = set()
    for db in dbs:
        frags = []
        for d in dets:
            yes, s, sk = belongs(db, d)
            if yes:
                frags.append((d, s, sk))
        frags.sort(key=lambda f: (f[0]["wall"] or (0, 0))[0])

        print(f"DB  {db['name']}  ({db['n']} rows)")
        if not frags:
            print("    NO detection-log matches this db.\n"); continue
        if len(frags) > 1:
            print(f"  ‼ ChurchPresenter RESTART detected — this STT session spans {len(frags)} CP runs; "
                  "all fragments below belong together (none lost).")

        for d, s, sk in frags:
            used_det.add(d["path"])
            conf = ("EXPLICIT session_id" if (d["session_id"] and d["session_id"] in db["session_id"])
                    else "HIGH" if s >= 0.7 else "MEDIUM" if s >= 0.4 else "LOW (review!)")
            detail = f"detections grounded {s:.0%}; seg-overlap {len(db['segs'] & d['segs'])}"
            if sk is not None:
                detail += f"; epoch skew {sk:.0f}s"
            print(f"  ├─ detection-log     : {d['name']}   [{conf}: {detail}]")
            # live-references / candidate / suggestion ride the SAME CP run's clock + segment space.
            for label, group in (("live-references", lrefs), ("candidate-log", cands),
                                 ("suggestion-outcomes", sugg)):
                hit = [g for g in group
                       if overlaps(g["wall"], d["wall"]) or (g["segs"] & d["segs"])]
                for g in hit:
                    print(f"  │    {label:<20}: {g['name']}  ({g['n']} rows)")
            if sk is not None and s >= 0.5 and sk > 120:
                print(f"  │    ⚠ content matches but STT-session epochs differ by {sk:.0f}s — likely "
                      "STT/CP machine clock skew (match still valid; don't trust raw timestamps).")
        print()

    orphan = [d["name"] for d in dets if d["path"] not in used_det]
    if orphan:
        print("Detection-logs with no db present (db not in this folder):", ", ".join(orphan))

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
