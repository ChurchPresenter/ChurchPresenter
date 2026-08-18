# BLE Reference Detection — Training Plan

## What this is for

The BLE engine detects Bible references from speech-to-text transcripts. Training data drives
improvements in four areas:

| Goal | Measure | Target |
|---|---|---|
| **Accuracy** | recall = TP / (TP + FN) | Detect more real references |
| **Precision** | precision = TP / (TP + FP) | Fewer wrong suggestions |
| **Speed** | median latency = (detection_ts − live_ts) ms | Engine beats the operator |
| **Continuation** | fewer premature detections, better verse-chain propagation | No wrong verse, no missed chain |

Ground truth is the **live-references log** — what the human operator actually put on screen.
The **detection-log** is what the engine suggested. The gap between them is what we fix.

**Nothing committed carries a date, a service id or a timestamp** — those identify when a specific
congregation met. Services are referred to by pseudonym (`S07`); the mapping to real session ids is
in `SESSIONS.md`, which is gitignored. Gap-table rows read `Status: FIXED`, without a date.

This file is the operating manual and is read at the start of every pass — keep it that way. What a
past pass *found* (traces, timestamps, sweeps, golden diffs) belongs in `SESSIONS.md`, which is local,
gitignored, and deliberately **not** read at the start of a pass — open it only to answer a specific
question.

---

## Artifact Types

| File | What it contains |
|---|---|
| `*.db` | Full service transcript (songs + sermons + announcements + prayers) |
| `detection-log-*.jsonl` | Every reference the engine suggested (book, chapter, verse, tier, source, ts, transcript) |
| `live-references-*.jsonl` | Every reference the operator put on screen (book, chapter, verse, ts_ms) |
| `candidate-log-*.jsonl` | Near-misses the engine considered but didn't fire (threshold tuning signal) |
| `sticky-log-*.jsonl` | Every sticky book/chapter change, **even when nothing was emitted** (`DetectionLogger.logStickyChange`, gated by `Config.logStickyChanges`) — independent of the other three; not read by `triage_report.py`, but read by `stickyAudit` (see Workflow below) |
| `operator-flags-*.jsonl` | Live operator feedback from ChurchPresenter's "Help Dev" mode (`STTTab`'s `helpDevMode` checkbox, `TrainingDataLogger.logOperatorFlag`) — the operator flags a BLE mistake in real time during a service via 3 buttons on the Bible tab: `kind="wrong_passage"` / `"premature"` (right book/chapter, wrong verse — a `Stabilizer` debounce signal, distinct from a parsing bug) both carry `book`/`chapter`/`verseStart`/`verseEnd` for whatever was live at click time; `kind="missed_passage"` carries only `ts_ms`/`segmentId` (no detection to anchor to — cross-reference the `.db` by timestamp during triage, same as any other FN). Not yet read by `triage_report.py`/`stickyAudit` — cross-reference by hand for now |
| `<sessionId>.db` | Also with "Help Dev" on: `STTManager` pulls a fresh read-only copy of the live STT session's `.db` from the STT server (`GET /api/transcription/status` + `/api/file-manager/download`) into this same folder every 60s while connected, so the whole session (db + all jsonl logs above) can be handed off as one folder — no more separate manual pull from the STT server after the fact. Subject to the same 30-day retention sweep as the jsonl logs (`TrainingDataLogger.cleanupOldLogsOnce()`) |

**Note**: DB files are whole-service transcriptions — 60–70 % of rows are music or non-sermon.
The training tools use `live-references.jsonl` as the time anchor, so only detections inside
the sermon window (bracketed by the first and last live-reference event) are counted. DB reads
are optional and only needed to see the exact text that was being spoken during an FN.

---

## Training Signals

Cross-referencing detection-log and live-references (within a ±90 s window per go-live event):

```
TP        — engine detected AND operator confirmed     → preserve; regression-test in ReferenceWatcherTest
PREMATURE — engine fired right book+chapter wrong verse first, then corrected
            e.g. "John 3:1" at T-4s then "John 3:16" at T-2s → operator went live on John 3:16
            → tune Stabilizer debounce or CP display hold
FP        — wrong detection inside a live-ref window  → suppress; add precision-negative test
FN        — operator confirmed, engine missed          → fix; find the missing pattern
```

---

## Workflow: New Logs Arrive

```
1.  Drop the session folder (db + all jsonl logs) into the local archive:
    ~/Desktop/bible-stt-history/   — one flat folder, every session ever recorded

2.  Run triage_report.py  (no DB needed — fast, tiny output):
        python tools/triage_report.py \
            --dlog detection-log-*.jsonl \
            --lref live-references-*.jsonl
    → paste the plain-text output into a conversation with Claude
    Read Coverage and Recall as a pair: Recall counts a LATE confirmation against the engine,
    Coverage does not. Coverage >> Recall means the engine is finding the right verses but
    trailing the operator — a Stabilizer/STT-finalisation question, not a parsing one, so do NOT
    open a parsing investigation on the LATE list.

2b. If a sticky-log-*.jsonl is present, ALSO run stickyAudit (no DB needed, seconds to run) —
    replaces the old "hand cross-reference every timestamp" step with an automated pre-triage:
        ./gradlew stickyAudit --args="/path/to/sticky-log-<session>.jsonl"
    → paste the plain-text output into the conversation alongside triage_report.py's. It classifies
    every recorded sticky jump using the SAME BookResolver.ALIASES/resolveStem the live engine uses
    (never drifts out of sync as the table grows), grouped by how well-supported the new book was:
        UNEXPLAINED          — no alias/stem match anywhere in the text — a brand-new bug pattern,
                                top priority (this is what "unexplained stale sticky" used to require
                                hours of manual timestamp-hunting to even notice)
        CHAPTER-CLEARED       — same book, chapter nulled — the exact shape of the
                                same-book-reflush bug; should be near-zero now, any hit is a
                                regression or a new variant
        SHORT ALIAS           — book resolved only via a short (<6-char) exact alias that might
                                double as ordinary vocabulary (the "бытие" shape)
        STEM OVER-EXTENSION   — book resolved only by extending well past its alias's stem (the
                                "открывает"/"откр" shape)
    CONFIDENT/OTHER rows are collapsed to a count — don't need review. See
    `src/main/kotlin/engine/tools/StickyAudit.kt` for the exact heuristic and why a naive
    "any stem match is risky" first version was too noisy to use (flagged 35 of 61 real jumps).

2a. Replay with the bibles the SERVICE ran, not whatever loads by default:
        -Dreplay.bibles='<primary>.spb,<secondary>.spb'   (bibleSettings in ~/.churchpresenter/settings.json)
    Two modules of the same translation can number Psalms differently — this machine carries a
    Synodal-numbered RST and a Hebrew-numbered one, both abbreviated "RST". Replaying with the wrong
    one shifts every Psalm by one, so explicit citations resolve to the neighbouring psalm and every
    Psalm comparison against the operator's log is off by one. Cross-check: the live detection-log's
    own chapter numbers must line up with the golden's.

2c. BEFORE believing any FN list, check the .db actually covers the service:
        max(ts_ms) in the .db  vs  the last live-references ts_ms
    The Help-Dev snapshot is pulled periodically, so an archived .db can stop early — S10
    ends 5 minutes short, mid-sentence, and all 7 of its "FNs" are references the engine did detect
    live with no transcript left in the archive to prove it. Anything past the last db row is
    unscoreable, not a miss.

3.  For each FN in the report:
        a. Identify the missing pattern from the reference text alone
           (if ambiguous, do a targeted DB query for that timestamp)
        b. If the FN looks like an unexplained stale/wrong sticky, check the stickyAudit output first
           (step 2b) — it now does the timestamp cross-referencing automatically
        c. Write a failing ReferenceWatcherTest (or ContinuationEngineTest for chapter-scan/history).
           If the fix generalizes a mechanism (not just one word/phrase), also add or extend a
           mechanism-level test — see Test Strategy below — so the next word that falls into the same
           trap is caught automatically, not just the one found today
        d. Fix the engine (BookResolver alias, ReferenceWatcher, ContinuationEngine, Config threshold)

3b. A live-references file with `sessionId: null` is NOT automatically unrelated. Builds before
    session stamping named it by process-start time instead. Check whether its ts_ms values fall
    inside the .db's window — if they all do, it is that service's ground truth
    (`live-references-S06.jsonl` is 28-for-28 inside `S05.db`).

4.  For each FP in the report:
        a. Read the trigger text (already in the report — from detection-log transcript field)
        b. Add a precision-negative assertNoEmit() test in ReferenceWatcherTest if not already covered

5.  For each PREMATURE in the report:
        a. Note the typical wrong→correct delay (shown in the report)
        b. If delay > 2s consistently: tune Stabilizer.minStabilityMs or CP display debounce

6.  Re-run triage_report.py → numbers must improve before committing

7.  For batch JSONL / eval_metrics table (optional, when processing multiple sessions):
        python tools/extract_training_samples.py \
            --db service.db --dlog ... --lref ... --out training-samples-sN.jsonl
        python tools/eval_metrics.py training-samples-s*.jsonl
```

---

## Test Strategy

| Test file | Role | What goes in it |
|---|---|---|
| `ReferenceWatcherTest.kt` | Explicit/sticky parsing regression guard | One test per distinct FN/FP pattern found in triage (book/chapter/verse parsing, ordinal resolution, split-utterance behavior), **plus** mechanism-level generalization tests (see below) |
| `ContinuationEngineTest.kt` | Content-matching regression guard | Sequential next-verse, chapter-scan, and chapter-history resolution/ambiguity-gate cases (synthetic in-memory fixtures — no real Bible files needed) |
| `DetectionLoggerTest.kt` | Logging-output guard | Minimal coverage of the sticky-change log's file/field shape |
| `StickyAuditTest.kt` | Triage-tool bucketing guard | That UNEXPLAINED stays the "look here first" category — every resolution route the live engine has must be one the auditor asks about |

**Test inputs are the shortest phrasing that reaches the gate**, never a verbatim sermon sentence —
a one-off utterance pins behaviour to that utterance and will not recur. Each carries a `GATE:` tag
(mechanism + file) and a `TRACE:` tag (session id + date); the recorded text stays in `SESSIONS.md`.
Verify a shortening by **mutation**, never by reading: reintroduce the bug and confirm the test still
fails. Intuitions about which words are load-bearing are unreliable — two shortenings looked correct,
compiled, passed, and had silently stopped exercising their gate.

Note for `ReferenceWatcherTest`: the suite runs with the **static** alias table only, while the live
engine also holds every book name the loaded SPB modules register at startup. Any behaviour that
depends on those (a module naming a book with an ordinary word) is invisible unless the test opts in
via `withRegisteredBookNames`, which restores the static table afterwards.

Service-level replay exists: `DbReplayTest` replays an archived `.db`
deterministically (injected clock) against a committed golden, and `replayEval` scores a replay
against the operator's live-references/suggestion-outcomes ground truth per matchType. Goldens
lock the CURRENT behavior so any engine change shows up as an explicit, reviewable diff — they
complement (not replace) the pattern-level unit tests below: each fix still gets a unit test
proving the generalized pattern, and the golden diff is regenerated + summarized in the same
commit.

**Mechanism-level tests**: a fix that generalizes a
mechanism (not just one word/transcript) should get a test that generalizes too, so the *next* word
that falls into the same trap is caught before a live session hits it, not after:
- **Invariant tests across multiple inputs** — e.g. "same-book reflush preserves chapter" is tested
  across several different books/chapters, not just the two real transcripts that first exposed it.
- **Fuzz tests over an extensible table** — e.g. the `AMBIGUOUS_BOOK_FORMS` corroboration gate is
  tested by iterating the table itself (with `kotlin.random.Random` + a fixed seed, hand-rolled — no
  property-testing library) rather than hardcoding today's two words, so adding a new ambiguous form
  to the table automatically gets fuzz coverage with no extra test-writing.
- **Growing negative corpus** — a single `NEGATIVE_CORPUS` list of real non-reference sentences,
  asserting none of them ever emits or hijacks an unset sticky. Append newly-confirmed-benign trigger
  text here as future sessions surface it, in addition to (not instead of) a dedicated named test for
  any actual fix — this keeps negative coverage growing every session instead of living only in
  one-off named tests.

---

## Known Engine Gaps

Fix in order: FN first, FP second, PREMATURE third, latency last. One row per gap — the diagnosis
narrative for anything marked FIXED is in `SESSIONS.md` under that date, not here.

| Gap | Example | Location | Status |
|---|---|---|---|
| German alias "judas" fires on the EN track | "when Judas betrayed him" → Jude, unconditionally (5 chars, clears the short-alias gate) | `AMBIGUOUS_BOOK_FORMS` | **FIXED** — the EN half of the earlier «Иуда» fix; found by genericizing a test whose recorded MT had garbled the word |
| `NOT_NUMBERS` had no effective test | "глава семьи" asserted only "nothing emitted"; with the entry removed it emits nothing either, it just primes chapter 7 | `ReferenceWatcherTest` | **FIXED** — assert the sticky, not the emission |
| Ezekiel's Russian alias was misspelled | table had one-и `иезекиль`; correct `Иезекииль` resolved to nothing | `BookResolver` | **FIXED** — both spellings kept; found by cross-checking an externally-written regex list |
| A book name starting with a number stem is read as a numeral | genitive `Второзакония` → the number 2, never reaching BookResolver | `ReferenceWatcher.isNumberRatherThanBook` | **FIXED** — longest match wins between the number and book lexicons; exact aliases were unaffected, so only inflected forms were losing |
| Canonical genitive book titles unresolvable | "Книга Иова/Руфи/Ионы", "Титу"→`тита` | `BookResolver` | **FIXED** — all ≤4 chars, so they inherit the short-alias gate and stay refused in bare prose |
| Per-word ASR confidence unused | `words_json`/`live_word_confidences` reach neither app nor engine | STT payload → `SttUpdate` | **OPEN, measured** — segment-aggregate confidence does NOT separate right from wrong detections. Only a token-local signal could work; see SESSIONS.md before re-testing |
| A multi-word book name misinflected by the STT splits into two books | "книгу пророка Плача Иеремия" → Lamentations THEN Jeremiah; interpret() keeps the last | `BookResolver.resolveStemPhrase` | **FIXED** — per-word stem join; whole 08-05 sermon had run against Jeremiah 3 |
| A prophet's name narrated as the author hijacks the sticky book | "Иеремия говорит…" mid-Lamentations → 3 tier-1 wrong-book auto-go-lives | `AMBIGUOUS_BOOK_FORMS` (nominative only) | **FIXED** — oblique cases stay ungated, so real citations are untouched |
| A "с N стиха" verse span corroborates a book named after it | "прочитаем с 22 стиха, Иеремия описывает" → sticky drifts to Jeremiah | `ReferenceWatcher.hasAmbiguousBookCorroboration` | **FIXED** — a FROM-bound number can't also be a chapter |
| `triage_report.py` compared display numbering to canonical ground truth | Synodal Ps 61:13 vs canonical 62:12 → every Psalm both FN and FP | `tools/triage_report.py` | **FIXED** — uses `canonicalStart`, matching `ReplayEval.sameVerse` |
| `triage_report.py` scored a late confirmation as FN *and* FP | engine names the verse +11s after the operator clicks | `tools/triage_report.py` | **FIXED** — new LATE bucket + Coverage metric; the old scoring read 47% recall at 85% coverage |
| MT renders «Плач Иеремии» as two book names joined by "and" | db row 160: "book of Lamentations and Jeremiah, chapter 3" | machine translation, upstream | **OPEN** (FP) — the one surviving wrong-book emission across the replayed services; self-corrects in ~1s |
| Short-alias corroboration too loose for counting/naming phrases | "Two songs" → Song of Solomon; "Job's first trial" → Job | `ReferenceWatcher.isCitationNumber` | **FIXED** — a SPELLED number only corroborates next to a chapter/verse keyword; digits still count anywhere |
| English plurals/possessives escape the short-alias gate | "songs"/"job's" fire where "song"/"job" are gated | `ReferenceWatcher.isShortAlias` | **FIXED** — a plural inherits the gate when its singular is a short alias for the same book ("james"→"jame", "acts"→"act" unaffected) |
| A Bible version name contains a book name | "New King James version" → James, mid-Psalm-14 | `ReferenceWatcher.isVersionNamePart` | **FIXED** |
| Short real-word aliases on the EN track | "am"→Amos and similar 2-3 char forms in prose | `BookResolver` per-language scoping | **OPEN** (FP) — raising `SHORT_ALIAS_MAX_LEN` to 5 was swept and rejected: it kills a real explicit `От Иоанна 3:6` |
| "27-й стих" parsed as chapter when no inline глава | s4r269 → John 27:28 FP | `ReferenceWatcher` ordinal disambiguation | **OPEN** (FP) |
| Cadence-adaptive sticky TTL | many book changes/min → shrink TTL | `ReferenceWatcher` / `Config` | **OPEN** (continuation) |
| Bare ambiguous numbered books | "Коринфянам"/"Книга царств" with no ordinal → which one? | `ReferenceWatcher.resolveNumberedBookAt` | **ACCEPTED** — deliberately unresolved, see Conventions below |
| PREMATURE verse detections | "John 3:1" before "John 3:16" | `Stabilizer` hold or CP debounce | **PARTIAL** — tiering (below) means these stage rather than go live |
| Sequential verse-by-verse reading latency | one verse per operator click, 4–15 s apart; engine confirms 5–10 s late | `ContinuationEngine` / `Stabilizer` | **PARTIAL** (verse-side coverage) + user-facing "Verse speed" knob. Residual is STT segment-finalization latency, outside the engine |
| Chapter-scope/history thresholds unvalidated | `chapterScopeMinAgreement`/`MinRatio` were starting guesses | `Config` | **FIXED (provisionally)** — gated structurally; values still provisional |
| Spelled-ordinal numbered books unresolvable | "Первая книга царств" → 1 Samuel | `ReferenceWatcher.resolveNumberedBookAt` | **FIXED** |
| Stem over-match on short RU aliases | "открывает"/"открылся" → Revelation; "повторить" → Deuteronomy | `ReferenceWatcher.classify` over-extension gate | **FIXED** |
| Short exact aliases fire from prose | "song"/"job"/"при" | `ReferenceWatcher.classify` | **FIXED** — single-token exact aliases ≤4 chars need corroboration |
| EN keyword-after-number citation order | "Job chapter 3 verse 2" parsed with chapter/verse swapped | `ReferenceWatcher.interpret` | **FIXED** — pending-keyword binding; colon binds buffered numbers |
| Same-book re-mention clobbers the sticky chapter | "...в 21 главе Откровения." nulls chapter 21 | `ReferenceWatcher.emit` | **FIXED** |
| Ambiguous common-word RU aliases hijack the sticky book | "Иоанну"/"бытие" in ordinary prose | `AMBIGUOUS_BOOK_FORMS` + `hasAmbiguousBookCorroboration` | **FIXED** |
| Inflected ambiguous words bypass the exact-token gate | genitive "бытия" → Genesis | `AMBIGUOUS_BOOK_STEMS` | **FIXED** |
| Verse keyword with no chapter keyword transposes the numbers | "Psalm 10, verse 13" → Psalm 30:10, shipped as `explicit` | `ReferenceWatcher.interpret` | **FIXED** |
| A short book name reaches the sticky through the stem path, ungated | "Иуда" (Judas, narrated) → Jude; "осия" → Hosea | `ReferenceWatcher.classify` (`shortAliasRefused`, `BookResolver.isRegisteredOnlyStem`) | **FIXED** — no golden change; also closes the ~40-min Hosea sticky episode |
| Ground-truth logs used display chapter/verse | Synodal Ps 23 vs canonical 24 → every Psalm scored as FN | `BibleViewModel.canonicalRefForDisplay` (main repo) | **FIXED** — see Critical Gotchas below |
| `.db` snapshot can stop before the service does | `S10.db` ends 5 min early, mid-sentence | `STTManager.disconnect` (main repo) | **FIXED** — final snapshot on disconnect; the truncation check stays in the Workflow |
| `stickyAudit` filed numbered-book resolutions as UNEXPLAINED | "во втором послании Коринфянам" | `engine.tools.StickyAudit` | **FIXED** |
| A trailing number became a chapter after a verse was already bound | "стихи 3-го стиха по 6-й" → ch 6 instead of vv 3-6 | `ReferenceWatcher.interpret` `flush()` | **FIXED** — removed 2 FPs, recovered a verse |
| A verse number spoken *before* its keyword was discarded | "Псалом 23, 1 стих" emitted chapter-only | `ReferenceWatcher.interpret` `Atom.VerseKw` | **FIXED** |
| "откр" (4-char Revelation alias) matches ordinary verbs 1-2 chars longer | "открой"/"открыл" → Revelation; the earlier gate needs ≥3 | `AMBIGUOUS_BOOK_STEMS` | **FIXED** — spelled-out "Откровении" matches the longer stem and is untouched |
| `replayEval` scored a re-shown verse as a miss | operator walked Psalm 24 twice; 3 of 5 "FNs" were the second pass | `ReplayEval` | **FIXED** — a go-live is covered when ANY detection names it in-window |
| A book named before its ordinal resolved to the wrong book | "Иоанн говорит в первом послании" → Gospel, as an auto-go-live tier-1 | `ReferenceWatcher.resolveNumberedBookAhead` | **FIXED** |
| A number introduced by "с"/"from" was taken as a chapter | "с 4-го стиха" → Proverbs **4**:3 tier-1 while the reading was Proverbs 3; "From the first verse" → Psalm 1 | `ReferenceWatcher.interpret` (`fromMark`) | **FIXED** — removed a wrong auto-go-live, added the correct verse |
| A tier-2 continuation fires on a partial with no cited verse | `19:1:1` at row 631, window `["С первого стиха.", "Псалом Давида."]` + partial, translation `["It will be Psalm 23.", "From the first verse."]` | `ReferenceWatcher` / `DetectionEngine` accumulation | **OPEN** — auto-go-live tier, self-corrects 3 s later at row 634. Those exact windows replayed through `process()` resolve correctly, so the corruption comes from an earlier call that emits nothing; reproduce by logging sticky changes during replay (`DetectionLogger.path` is nulled in `DbReplay`, and only `replay.*` properties reach the test JVM — see `build.gradle.kts`) |
| `chapter-scan` chips are never clicked though usually right | 12/13 TP across 8 services, 0 of 10 accepted | `BibleViewModel` auto-follow tiering | **RESOLVED** — promoted to auto-go-live |
| `chapter-history` costs precision and earns nothing | 1 TP in 8 services, never accepted, 94 emissions/0 TP in one service | `Config.chapterHistoryEnabled` | **RETIRED** — flag off by default; removing it dropped 2 emissions across 8 services with zero recall loss |
| `replayEval` compared the matched module's own numbering | ground truth is canonical since the `display*` fields were added; two RST modules here number Psalms differently | `ReplayEval.sameVerse` | **FIXED** — canonical comparison, legacy display rows still matched the old way |

### Conventions (decisions, not history)

- **A bare numbered book with no ordinal stays unresolved.** "Коринфянам"/"Книга царств" with a
  marker but no ordinal resolves to nothing — there is no convention that a bare mention means the
  1st, so guessing would be wrong more often than right. John/Peter are the sole exception: a marker
  alone ("Послание Иоанна") conventionally means the 1st.
- **A bare ambiguous word with no corroboration stays unresolved**, at a known recall cost: a bare
  "Бытие" whose chapter arrives much later will not prime the sticky. No textual way to tell it from
  ordinary vocabulary.
- **Never fabricate a missing verse/chapter/book.** Prime the sticky and stay silent instead.
- **Extensible tables — add forms as data surfaces them, don't special-case at the call site**:
  `NUMBERED_BOOK_FORMS` (further inflections such as genitive "тимофея"/"коринфян" go here),
  `AMBIGUOUS_BOOK_FORMS` (exact tokens), `AMBIGUOUS_BOOK_STEMS` (all case endings).
  `AMBIGUOUS_BOOK_STEMS` is deliberately narrow — stemming "иоанну" would wrongly gate the
  already-correct genitive/nominative forms.

### Mechanisms that already exist (don't rebuild)

- **chapter-scan** — book+chapter alone is enough; `ContinuationEngine.checkChapterScope` scores every
  verse in the known chapter against what was spoken, with a floor + margin-over-runner-up gate.
- **chapter-history** — **OFF by default** (`Config.chapterHistoryEnabled = false`; see the gap
  table for the evidence). The mechanism still exists: the candidate pool becomes
  `{sticky} ∪ {UtteranceState.chapterHistory}`, so a preacher revisiting an earlier chapter resolves
  without restating it, logged as a distinct `matchType` because it is rarer and riskier. If it is
  ever switched back on, judge it on `chapter-history`-tagged rows specifically.
- **Reverse lookup needs no history** — quoting a passage by its text alone already works;
  `ReverseLookup.search()` runs BM25 over the whole Bible on every utterance.
- **`stickyAudit`** — auto-triages a sticky log against the engine's own alias/stem data. It cannot
  see SPB-registered book names, so an UNEXPLAINED row can also mean "resolved via a module's own
  book name".
- **`DbReplayTest` / `replayEval`** — deterministic service replay against a local golden, and
  per-matchType scoring against operator ground truth.

### Provisional constants (all want more data before being trusted)

`chapterScopeMinAgreement` / `chapterScopeMinRatio`; verse-coverage floors (0.45 scan / 0.6 history);
`SHORT_ALIAS_MAX_LEN` = 4; `STEM_MAX_EXTENSION_UNCORROBORATED` = 3; Verse speed presets
(Balanced `continuationMinCoverage` 0.5 / Fast 0.45, from a 5-value sweep over 4 sessions —
`DetectionLogger` stamps which preset was active on every row).

### Two standing caveats

- **Russian-validated, not English-validated.** Every gate above was tuned against Russian sermon
  speech plus its English *machine translation*. Native English preaching is barely represented, and
  the bugs found in the one such session trace specifically to English-source
  rows — natural English citation grammar and English book abbreviations in prose. Give an
  English-heavy session extra scrutiny, and check `source_language` before assuming a short-alias FP
  is the same well-tuned Russian-track class.
- **Tiered auto-follow changes the stakes per matchType.** `explicit`, `continuation` and
  `chapter-scan` push to the output screen automatically; only `reverse` stages the
  browse view for operator confirmation (`BibleViewModel`'s `instantGoLive` predicate, main repo).
  `chapter-history` is retired (`Config.chapterHistoryEnabled = false`). A false positive in those
  three tiers goes live unattended, so **they are the highest-stakes thing to get right** — and
  "unattended wrong-verse events per service" is the number this training loop exists to drive to
  zero. A `reverse` false positive is a precision nuisance, not a live-service problem. Both
  `live-references` and `suggestion-outcomes` carry `matchType`, so acceptance per tier stays
  measurable — re-check it after this promotion, since `chapter-scan` chips should now be rare
  (the verse is already live) rather than merely ignored.
  **Watch the promotion.** `chapter-scan` was 12/13 correct as a staged tier, so going live unattended
  should put roughly **one wrong verse on screen per ~8 services** — an accepted trade for the twelve
  it gets right, not a surprise. Reconsider it if a Help-Dev `wrong_passage` flag lands on a
  `chapter-scan` detection, or if the tier shows up in a pass's wrong-verse-live count; reverting is a
  one-word edit to `BibleViewModel`'s `instantGoLive` predicate.
---

## Critical Gotchas: Book Numbering

### Book id ≠ book position

The engine emits a **canonical book id** (1=Genesis … 62=1 John … 66=Revelation).
A Bible's display list (`Bible.getBooks()`) is in the **file's order**, which is NOT canonical:

- The **Russian Synodal** Bible places the General Epistles (James, 1–2 Peter, 1–3 John, Jude)
  **right after Acts**, before Paul's letters. So 1 John sits at display **index 47**.
- Mapping `index = canonicalId − 1` sends **1 John (62) → index 61 → 2 Timothy**. Every NT
  epistle shifts. This was a real regression.
- **Fix**: map by book id field, not position: `Bible.getDisplayIndexForBookId(id)`.

### Psalm numbering: Hebrew/EN Ps 23 = Synodal Ps 22

LXX merges Ps 9+10, shifting most of the Psalter by one.

- The engine forwards `canonicalCodeStart/End` (Hebrew numbering).
- The app resolves display number via `Bible.getVerseDetailsByCode()` using the per-Bible
  `codeToDisplayMap`. Works for any primary Bible.
- **Never** pass the engine's raw `chapter` straight to display when primary ≠ the engine's
  matched translation.

### Ground-truth logs must use canonical numbering — book, chapter AND verse

`live-references` must log the **canonical** reference (`BXXXCXXXVXXX`, Hebrew numbering), not the
display position and not the primary Bible's own chapter/verse numbers.

Chapter and verse were display-numbered before the `display*` fields were introduced, which scored every Psalm as an FN (Synodal
23 = canonical 24). Map with `BibleViewModel.canonicalRefForDisplay` → `Bible.getCodeReference`; all
three logs (`live-references`, `suggestion-outcomes`, `operator-flags`) go through `BibleViewModel`
and carry `display*` fields alongside the canonical ones.

**When triaging a recording that carries no `display*` fields**: its logs are display-numbered — Psalms off by
one against the engine (except Ps 1–8 and 148–150) and `suggestion-outcomes` book ids are display
positions. The absence of `display*` fields is how to tell which convention a file follows.

---

## File Locations

```
ChurchPresenter-BLE/
  TRAINING_PLAN.md                ← this file (operating manual — read at the start of every pass)
  SESSIONS.md                     ← per-pass history, gitignored; NOT read at pass start
  PROMPT.md                       ← the prompt that starts a pass
  build.gradle.kts                ← `stickyAudit` JavaExec task (engine.tools.StickyAuditKt)
  tools/
    triage_report.py              ← quick plain-text report, no DB needed  ← START HERE
    extract_training_samples.py   ← compact JSONL per session (DB needed for FN anchor text)
    eval_metrics.py               ← precision / recall / premature / latency table
    match_training_data.py        ← artifact pairing helper
  src/main/kotlin/engine/tools/
    StickyAudit.kt                 ← sticky-log auto-triage, `./gradlew stickyAudit --args="<path>"`
  src/test/kotlin/engine/
    ReferenceWatcherTest.kt       ← explicit/sticky parsing regression guard; grows with each new FN/FP,
                                     plus mechanism-level invariant/fuzz tests and a growing negative corpus
    ContinuationEngineTest.kt     ← chapter-scan / chapter-history regression guard
    DetectionLoggerTest.kt        ← sticky-change log output guard
    StickyAuditTest.kt            ← stickyAudit bucketing guard

~/Desktop/bible-stt-history/   (local archive, never committed)
  detection-log-<session>.jsonl
  live-references-<session>.jsonl
  candidate-log-<session>.jsonl   (when present)
  sticky-log-<session>.jsonl      (when present — run through `stickyAudit`, see Workflow step 2b)
  training-samples-<session>.jsonl  ← produced by extract_training_samples.py
```
