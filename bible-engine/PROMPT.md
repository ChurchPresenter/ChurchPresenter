# Prompt: BLE session validation / training pass (paste this to start a fresh session)

New session files are in `<session folder>` (a `.db` plus `bible-stt-logs/` with
`detection-log-*.jsonl`, `sticky-log-*.jsonl`, and possibly `live-references-*.jsonl`) —
I'll give you the path when I paste this. Read `TRAINING_PLAN.md` (in this directory,
`ChurchPresenter-BLE/`) before touching anything.

Bibles for replay: the **primary and secondary the service actually ran with** — read them from
`bibleSettings` in `~/.churchpresenter/settings.json`, don't pick by filename. Two modules of one
translation can number Psalms differently (this machine has a Synodal-numbered and a Hebrew-numbered
"RST"), and the wrong one shifts every Psalm by a chapter. `AppConfig.discoverBibleRoot()` finds the
folder.

## Hard rules (before anything else)

- **Committed files carry no dates, service ids or timestamps.** A session id is a service's date
  AND start time, so it identifies when a specific congregation met; bare dates leak the same thing
  indirectly, since work follows services. Refer to a service by its pseudonym (`S07`) — the
  pseudonym→id mapping lives in `SESSIONS.md`, which is gitignored. Gap-table rows are
  `Status: FIXED`, with no date.
- **Never commit or push any artifact derived from recorded sessions** — goldens, detection/sticky
  logs, `.db` files, triage output. `src/test/resources/replay/` is gitignored and stays local;
  session data is private even when it's refs-only.
- **Don't commit anything at all unless I explicitly say "commit".** Implement and verify; leave
  the commit decision to me.

## Phase A — validate the session (always do this first)

1. Inspect the `.db` read-only: row/partial/final/segment counts, session duration,
   `translation_ts_ms` lag stats (min/avg/max) — report the lag trend vs. previous sessions.
   Contract to confirm: partials are cumulative snapshots with incrementing `partial_seq`;
   `translation_ts_ms` shares the `ts_ms` clock.
2. Generate a local golden (determinism guard runs inside the test):
   ```
   ./gradlew test --tests "org.churchpresenter.bibleengine.replay.DbReplayTest" \
     -Dreplay.db=<path to the .db> -Dreplay.updateGolden=true \
     '-Dreplay.bibles=<primary>.spb,<secondary>.spb' \
     --rerun-tasks
   ```
   Then re-run WITHOUT `-Dreplay.updateGolden` to confirm comparison mode passes, and confirm
   `git status` stays clean (golden must be ignored).
3. Diff the replay golden against the live detection-log's emitted refs (ref + matchType counts).
   Near-1:1 is expected (small jitter is normal — live sees every streaming partial, the DB stores
   throttled snapshots). Investigate any wildly divergent ref against the DB row text. Note total
   emissions as the spam check.
4. Ground truth check: a `live-references` file counts only if its `sessionId` matches this session
   (`sessionId: null` = unrelated app testing). If it matches, run the `replayEval` gradle task for
   per-matchType precision/recall. If not, say so and skip scoring.
5. Report: contract health, replay-vs-live verdict, translation lag, anything for the STT dev.

## Phase B — training pass (only when there is matching ground truth, or I point at specific misses)

Follow the workflow in `TRAINING_PLAN.md`: run `tools/triage_report.py` on the detection-log +
live-references, then work each FN/FP/PREMATURE in priority order. Use the `.db` only when a pattern
is ambiguous from the report text. If a `sticky-log-*.jsonl` is present, also run
`./gradlew stickyAudit --args="<path>"` — it auto-classifies every sticky book/chapter jump against
the live engine's own alias/stem data (found the Revelation/Proverbs pattern and two more bugs
before manual cross-referencing would have).

Discipline rules from past passes — follow these, they each earned their place:

1. **Chase the real root cause, not the nearest patch.** When a test phrase and the actual trigger
   phrase differ (e.g. an abbreviated/digit form vs. the natural spoken form), don't write the test
   against whichever one happens to pass easily — test the phrasing that will actually recur. A very
   precise, unlikely-to-repeat test string is a sign the *pattern* behind it needs fixing instead.

2. **Prefer generalizing an existing mechanism over hardcoding one more case.** Check
   `BookResolver.kt`, `ReferenceWatcher.kt`, `ContinuationEngine.kt`, `AgreementScorer.kt`,
   `BibleIndex.kt`, and `EngineTranslation`'s `byChapter`/`byBCV` lookups for reusable primitives
   before inventing new ones.

3. **Never fabricate a value the STT never said.** A code path defaulting a missing
   verse/chapter/book to a guess (`?: 1` or similar) is a bug, not a convenience — stay silent and
   prime context (sticky, chapter history) instead. This bit us twice in one session via two
   different code paths — check *all* the places a similar default could hide.

4. **Don't guess tuning constants.** If a threshold genuinely needs real data, pick a documented
   starting value, mark it provisional in `Config`, and leave it for the next session's data.

5. **For anything beyond a one-line/obvious fix, use Plan Mode.** Read the relevant source fully,
   trace the data flow by hand, write the plan with concrete file/function references. Ask when a
   design choice is genuinely mine to make.

6. **Every fix needs a test using the real trigger text**, in `ReferenceWatcherTest.kt` or
   `ContinuationEngineTest.kt` (synthetic in-memory fixtures preferred for `ContinuationEngine`).
   Run `bash gradlew test` (full suite) before calling anything done.

7. **When a fix generalizes a mechanism, also add a mechanism-level test** — an invariant across
   several inputs or a seeded fuzz over an extensible table (e.g. `AMBIGUOUS_BOOK_FORMS`), so the
   next phrase falling into the same trap is caught automatically. See Test Strategy in
   `TRAINING_PLAN.md`.

8. **Any engine behavior change must regenerate the affected local goldens** with
   `-Dreplay.updateGolden=true` and summarize the diff (events added/removed and why) in your
   report — the golden diff is the review artifact, even though goldens are no longer committed.

9. **Write findings to `SESSIONS.md`, not `TRAINING_PLAN.md`.** `TRAINING_PLAN.md` is read at the
   start of every pass and must stay under ~250 lines, so a fix earns **one gap-table row**
   (`Status: FIXED`) plus — only when it changes how future work is done — a line in Workflow /
   Test Strategy / Conventions / Gotchas. The narrative (real traces, timestamps, sweep tables, golden
   diffs, rejected hypotheses) goes in `SESSIONS.md` under a dated `## YYYY-MM-DD` heading, newest
   first. `SESSIONS.md` is gitignored and is **not** read at the start of a pass — open it only to
   answer a specific question ("has this shape been seen before?", "why is that constant 0.45?").
   It is the *only* file findings go in: never `AGENT.md`, `CLAUDE.md`, `FEATURES.md`,
   `DEVELOPMENT_GUIDE.md`, `CODING_STANDARDS.md`, `README.md`, nor a plan or agent-instruction file.

Start with Phase A. Only enter Phase B if there's ground truth or I ask for fixes.
