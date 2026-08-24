#!/usr/bin/env bash
#
# Runs only the jvmTest classes plausibly affected by what you have changed.
#
#   ./test-changed.sh                 # working tree + everything since origin/main
#   ./test-changed.sh --staged-only   # index + working tree only, ignore the branch history
#   ./test-changed.sh --dry-run       # print the gradle command, run nothing
#   ./test-changed.sh --deep          # follow one extra hop through jvmMain callers
#   ./test-changed.sh --no-fallback   # report and exit 0 rather than running the full suite
#
# This is an INNER-LOOP tool. It selects tests by name and by whole-word symbol references, which
# is a heuristic — run the full `./gradlew :composeApp:check` before you commit. What it misses:
#
#   * Transitive reach. A test exercising ScheduleViewModel never names the Constants value it
#     depends on three layers down. `--deep` buys one hop through jvmMain callers and no more.
#   * The 64 *ScreenshotTest suites compose whole app previews, so ANY composable change can move
#     pixels in a suite that never mentions the composable. Touching composables/ or tabs/? Add
#     `--tests '*ScreenshotTest'` by hand.
#   * References made through strings or reflection: testTag literals, Ktor route paths,
#     localisation keys, JSON discriminators, Class.forName.
#   * Deletes and renames. A deleted file declares no symbols; a rename looks like an unrelated
#     add plus a delete.
#   * Non-Kotlin inputs (.spb fixtures, sentry.properties, the version catalog) are reported but
#     never mapped to a suite.
#
# Over-selection is harmless — it only costs time. Under-selection is the risk the list above is
# about, which is why "no test matched" falls back to the full suite rather than reporting green.
#
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

MAIN_SRC="composeApp/src/jvmMain/kotlin composeApp/src/commonMain/kotlin"
# The other modules of this build, by directory — each maps to a `:<dir>:test` task.
MODULE_DIRS="converter|companion-satellite|theme|core-models|bible-engine|lottieGenerator|crossword|presentation-engine|settings|diagnostics|atem|planning-center|bible-formats|songlibrary|song-chords|bible|dictionary|dictionary-tab|dictionary-settings-tab|announcements-tab|lower-third-tab|lower-third-settings-tab|companion-server|statistics|ui-components"
TEST_SRC="composeApp/src/jvmTest/kotlin"
MAX_PATTERNS=120          # past this a full run is cheaper than a vast --tests filter
BASE_REF="${BASE_REF:-origin/main}"

DRY_RUN=0; DEEP=0; FALLBACK=1; STAGED_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --dry-run)     DRY_RUN=1 ;;
    --deep)        DEEP=1 ;;
    --no-fallback) FALLBACK=0 ;;
    --staged-only) STAGED_ONLY=1 ;;
    -h|--help)     sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

# ── 1. What changed ──────────────────────────────────────────────────────────
changed_files() {
  git diff --name-only HEAD                       # unstaged + staged vs HEAD
  git ls-files --others --exclude-standard        # brand-new untracked files
  if [ "$STAGED_ONLY" -eq 0 ] && git rev-parse --verify --quiet "$BASE_REF" >/dev/null; then
    mb="$(git merge-base HEAD "$BASE_REF" 2>/dev/null || true)"
    if [ -n "$mb" ]; then git diff --name-only "$mb" HEAD; fi
  fi
}

ALL_CHANGED="$(changed_files | sort -u)"
if [ -z "$ALL_CHANGED" ]; then
  echo "nothing changed."; exit 0
fi

# The app's own Kotlin only. Every other module of this build has its own suite that
# :composeApp:jvmTest cannot run, so a --tests pattern derived from one would match nothing. Those
# are reported at the end instead.
APP_KT='^composeApp/src/(jvmMain|commonMain|jvmTest|commonTest)/kotlin/.*\.kt$'

# Only files that still exist: a pattern derived from a deleted file matches nothing, and Gradle
# fails the whole invocation with "No tests found for given includes".
KT_CHANGED="$(printf '%s\n' "$ALL_CHANGED" | grep -E "$APP_KT" | while read -r f; do
  [ -f "$f" ] && echo "$f"
done || true)"
NON_KT="$(printf '%s\n' "$ALL_CHANGED" | grep -vE "$APP_KT" | grep -vE "^($MODULE_DIRS)/" || true)"

# Which modules of this build a change touched. They are all modules of the root build now, so each
# is a task on this wrapper — but :composeApp:jvmTest still does not run any of their suites.
MODULES_CHANGED="$(printf '%s\n' "$ALL_CHANGED" \
  | sed -nE "s#^($MODULE_DIRS)/.*#\1#p" \
  | sort -u || true)"

report_modules() {
  [ -n "$MODULES_CHANGED" ] || return 0
  echo
  echo "modules touched — :composeApp:jvmTest does not run their suites:"
  for m in $MODULES_CHANGED; do
    echo "  ./gradlew :$m:test"
  done
}

if [ -z "$KT_CHANGED" ]; then
  echo "no app Kotlin changed (build files / resources / fixtures / other modules only):"
  printf '%s\n' "$NON_KT" | sed 's/^/  /'
  report_modules
  [ "$FALLBACK" -eq 1 ] || { echo ">> --no-fallback: running nothing."; exit 0; }
  echo ">> nothing to map from; running the full suite."
  exec ./gradlew :composeApp:jvmTest -PfastTest
fi

# ── 2. Symbols to look for ───────────────────────────────────────────────────
# The file's own base name, every top-level type it declares, and every capitalised top-level
# function (i.e. every @Composable). Deliberately NOT lowercase functions or properties: `fun load`
# matches half the suite and the result stops being a filter.
symbols_of() {
  local f="$1"
  basename "$f" .kt
  grep -oE '^[[:space:]]*(public |internal |private |abstract |sealed |open |data |value |enum |annotation )*(class|object|interface)[[:space:]]+[A-Za-z_][A-Za-z0-9_]*' "$f" 2>/dev/null | awk '{print $NF}'
  grep -oE '^[[:space:]]*(public |internal |private )?fun[[:space:]]+[A-Z][A-Za-z0-9_]*' "$f" 2>/dev/null | awk '{print $NF}'
  # A file with neither (a top-level `val`, a lowercase-only helper) would otherwise leave the
  # function's status at grep's 1, which `set -e` turns into a silent abort at the call site.
  return 0
}

# A jvmTest file becomes a --tests pattern only if it declares a class of its own name. Support
# files (MediaTabTestSupport.kt, AppPreviewSupport.kt, TestSingletons.kt) fail that check and fall
# through to the symbol search, which correctly fans out to every suite that uses them.
fqn_of_test_file() {
  local f="$1" cls pkg
  cls="$(basename "$f" .kt)"
  grep -qE "^[[:space:]]*(internal |public |abstract )?class[[:space:]]+${cls}\b" "$f" || return 1
  pkg="$(grep -m1 '^package ' "$f" | awk '{print $2}')"
  [ -n "$pkg" ] || return 1
  echo "${pkg}.${cls}"
}

PATTERNS=""; SYMS=""; UNMAPPED=""

for f in $KT_CHANGED; do
  case "$f" in
    composeApp/src/jvmTest/*)
      if fqn=$(fqn_of_test_file "$f"); then
        PATTERNS="$PATTERNS
$fqn"
      else
        SYMS="$SYMS
$(symbols_of "$f")"          # a changed test-support file
      fi
      ;;
    *)
      SYMS="$SYMS
$(symbols_of "$f")"
      # --deep: one extra hop. A change to a leaf util is usually exercised through its callers,
      # and those callers are what the tests actually name.
      if [ "$DEEP" -eq 1 ]; then
        for s in $(symbols_of "$f"); do
          for caller in $(grep -rlw --include='*.kt' -- "$s" $MAIN_SRC 2>/dev/null); do
            SYMS="$SYMS
$(symbols_of "$caller")"
          done
        done
      fi
      ;;
  esac
done

# ── 3. Symbols → test classes ────────────────────────────────────────────────
SYMS="$(printf '%s\n' "$SYMS" | sed '/^$/d' | sort -u)"
for s in $SYMS; do
  hits=""
  # (a) naming convention: Foo.kt -> Foo*Test.kt, FooBarTest.kt, ...
  for t in $(find $TEST_SRC -name "${s}*Test*.kt" 2>/dev/null); do hits="$hits $t"; done
  # (b) any test file mentioning the symbol as a whole word
  for t in $(grep -rlw --include='*.kt' -- "$s" $TEST_SRC 2>/dev/null); do hits="$hits $t"; done
  found=0
  for t in $hits; do
    if fqn=$(fqn_of_test_file "$t"); then
      PATTERNS="$PATTERNS
$fqn"
      found=1
    fi
  done
  if [ "$found" -eq 0 ]; then UNMAPPED="$UNMAPPED $s"; fi
done

PATTERNS="$(printf '%s\n' "$PATTERNS" | sed '/^$/d' | sort -u)"
COUNT="$(printf '%s\n' "$PATTERNS" | sed '/^$/d' | grep -c . || true)"

# ── 4. Decide ────────────────────────────────────────────────────────────────
if [ "$COUNT" -eq 0 ]; then
  echo "Kotlin changed but no test class references it:"
  printf '%s\n' "$KT_CHANGED" | sed 's/^/  /'
  echo "  unmatched symbols:$UNMAPPED"
  if [ "$FALLBACK" -eq 0 ]; then
    echo ">> --no-fallback: running nothing."; exit 0
  fi
  echo ">> That usually means the change is untested. Running the full suite so you find out."
  exec ./gradlew :composeApp:jvmTest -PfastTest
fi

if [ "$COUNT" -gt "$MAX_PATTERNS" ]; then
  echo "$COUNT candidate suites — past the point where filtering pays. Running the full suite."
  exec ./gradlew :composeApp:jvmTest -PfastTest
fi

ARGS=""
while read -r p; do [ -n "$p" ] && ARGS="$ARGS --tests $p"; done <<EOF
$PATTERNS
EOF

echo "$COUNT suite(s) selected:"
printf '%s\n' "$PATTERNS" | sed 's/^/  /'
if [ -n "$NON_KT" ]; then
  echo "note: non-Kotlin changes NOT covered by this selection:"
  printf '%s\n' "$NON_KT" | sed 's/^/  /'
fi
report_modules

CMD="./gradlew :composeApp:jvmTest -PfastTest$ARGS"
echo; echo "$CMD"
if [ "$DRY_RUN" -eq 1 ]; then exit 0; fi
exec $CMD
