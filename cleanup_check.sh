#!/bin/bash
# ChurchPresenter Code Quality Report
# Run this before committing to verify code quality standards

echo "╔════════════════════════════════════════════════════════╗"
echo "║     ChurchPresenter Code Quality Report                ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

# Count wildcard imports
WILDCARDS=$(grep -r 'import.*\.\*' --include='*.kt' composeApp/src/ 2>/dev/null | wc -l | tr -d ' ')
echo "📦 Wildcard imports: $WILDCARDS"
if [ "$WILDCARDS" -gt 0 ]; then
    echo "   ❌ FAIL - Should be 0"
else
    echo "   ✅ PASS"
fi

# Count Material 2 imports (androidx.compose.material.icons.* is Material 3's icon library and
# is explicitly allowed — see DEVELOPMENT_GUIDE.md's "Use Material 3 icons" rule — so it's excluded
# here rather than being flagged as a false Material 2 violation)
MATERIAL2=$(grep -r 'import androidx.compose.material\.[^3]' --include='*.kt' composeApp/src/ 2>/dev/null | grep -v 'import androidx\.compose\.material\.icons' | wc -l | tr -d ' ')
echo "🎨 Material 2 imports: $MATERIAL2"
if [ "$MATERIAL2" -gt 0 ]; then
    echo "   ❌ FAIL - Should be 0"
else
    echo "   ✅ PASS"
fi

# Count debug prints (word-boundaried so e.g. "getCaCertFingerprint(" doesn't match "print(" as a
# substring; System.err.println is excluded — DEVELOPMENT_GUIDE.md's Decision Log keeps those
# intentionally for VLC/JCEF/WebView/CompanionServer error diagnostics, they're not debug spam)
PRINTS=$(grep -rE '\b(println|print)\(' --include='*.kt' composeApp/src/jvmMain/kotlin/ 2>/dev/null | grep -v 'System\.err\.println' | wc -l | tr -d ' ')
echo "🐛 Debug print statements: $PRINTS"
if [ "$PRINTS" -gt 0 ]; then
    echo "   ⚠️  WARN - Should be 0"
else
    echo "   ✅ PASS"
fi

# Count fully qualified type names used inline (e.g. in a function signature). Three kinds of line
# are excluded, because none of them is the thing the rule forbids — writing the long form where an
# import already binds the name:
#
#   import ...                  the correct, required way to reference a type
#   @file:OptIn(...::class)     a file-level annotation, which sits above the import block and is
#                               conventionally written out in full; 283 of these alone, so leaving
#                               them in made the count permanently non-zero
#   * / // / /*                 KDoc links like [androidx.compose.ui.test.ComposeUiTest], which need
#                               the full path precisely when the type is NOT imported
#
# Without those exclusions this reported 306 against a documented target of 0 — a check that cannot
# pass is a check nobody reads, and it was hiding 21 real violations in the noise.
QUALIFIED=$(grep -rE 'androidx\.compose\.[a-z]*\.[a-zA-Z]*\.[A-Z]' --include='*.kt' composeApp/src/ 2>/dev/null \
    | grep -vE '^\S*:\s*import\b' \
    | grep -vE '@file:OptIn|@OptIn' \
    | grep -vE '^\S*:\s*(\*|//|/\*)' \
    | wc -l | tr -d ' ')
echo "📝 Fully qualified type names: $QUALIFIED"
if [ "$QUALIFIED" -gt 0 ]; then
    echo "   ⚠️  WARN - Should be 0"
else
    echo "   ✅ PASS"
fi

# ── The modules ──────────────────────────────────────────────────────────────────────────────
# Every other module of the build (settings.gradle.kts), main and test sources. The print check
# covers main sources only and skips what prints on purpose: CLI tools under a `tools/` package,
# bible-engine's standalone-launch files (Main.kt, AppConfig.kt), and lines behind its
# `verboseLog` flag. DEVELOPMENT_GUIDE.md's decision log lists the same exceptions.
echo ""
echo "Modules:"
MODULES=$(grep -oE 'include\(":[A-Za-z-]+"\)' settings.gradle.kts | sed -E 's/include\(":(.*)"\)/\1/' | grep -v '^composeApp$')
MOD_CRITICAL=0
MOD_WARNINGS=0
MOD_DIRTY=0
for MOD in $MODULES; do
    [ -d "$MOD/src" ] || continue
    M_WILD=$(grep -rE '^import .*\.\*$' --include='*.kt' "$MOD/src" 2>/dev/null | wc -l | tr -d ' ')
    M_M2=$(grep -r 'import androidx\.compose\.material\.' --include='*.kt' "$MOD/src" 2>/dev/null \
        | grep -v 'import androidx\.compose\.material\.icons' | wc -l | tr -d ' ')
    M_PRINT=$(grep -rE '\b(println|print)\(' --include='*.kt' "$MOD/src/main" 2>/dev/null \
        | grep -v 'System\.err\.println' \
        | grep -vE '/tools/|bible-engine/.*/(Main|AppConfig)\.kt:|verboseLog' | wc -l | tr -d ' ')
    M_FQN=$(grep -rE 'androidx\.compose\.[a-z]*\.[a-zA-Z]*\.[A-Z]' --include='*.kt' "$MOD/src" 2>/dev/null \
        | grep -vE '^\S*:\s*import\b' \
        | grep -vE '@file:OptIn|@OptIn' \
        | grep -vE '^\S*:\s*(\*|//|/\*)' \
        | wc -l | tr -d ' ')
    MOD_CRITICAL=$((MOD_CRITICAL + M_WILD + M_M2))
    MOD_WARNINGS=$((MOD_WARNINGS + M_PRINT + M_FQN))
    if [ $((M_WILD + M_M2 + M_PRINT + M_FQN)) -gt 0 ]; then
        MOD_DIRTY=$((MOD_DIRTY + 1))
        printf "   ❌ %-22s wildcard %s · Material 2 %s · prints %s · FQN %s\n" "$MOD" "$M_WILD" "$M_M2" "$M_PRINT" "$M_FQN"
    fi
done
if [ "$MOD_DIRTY" -eq 0 ]; then
    echo "   ✅ PASS — $(echo "$MODULES" | wc -l | tr -d ' ') modules clean"
fi

echo ""
echo "Building project to check for warnings..."
echo ""

# Build and check for unused code. `compileKotlin` with no project path compiles every JVM module;
# stderr is captured too, which is where the compiler's warnings go.
./gradlew compileKotlinJvm compileKotlin --no-daemon > /tmp/build_output.txt 2>&1

UNUSED_IMPORTS=$(grep 'Unused import' /tmp/build_output.txt | wc -l | tr -d ' ')
echo "📥 Unused imports: $UNUSED_IMPORTS"

UNUSED_PARAMS=$(grep 'Parameter.*never used' /tmp/build_output.txt | wc -l | tr -d ' ')
echo "🔧 Unused parameters: $UNUSED_PARAMS"

UNUSED_FUNCS=$(grep 'Function.*never used' /tmp/build_output.txt | wc -l | tr -d ' ')
echo "⚙️  Unused functions: $UNUSED_FUNCS"

UNUSED_PROPS=$(grep 'Property.*never used' /tmp/build_output.txt | wc -l | tr -d ' ')
echo "💾 Unused properties: $UNUSED_PROPS"

echo ""
echo "╔════════════════════════════════════════════════════════╗"
echo "║                    Summary                              ║"
echo "╚════════════════════════════════════════════════════════╝"

# Calculate pass/fail
CRITICAL=0
WARNINGS=0

if [ "$WILDCARDS" -gt 0 ]; then CRITICAL=$((CRITICAL+1)); fi
if [ "$MATERIAL2" -gt 0 ]; then CRITICAL=$((CRITICAL+1)); fi
if [ "$PRINTS" -gt 0 ]; then WARNINGS=$((WARNINGS+1)); fi
if [ "$QUALIFIED" -gt 0 ]; then WARNINGS=$((WARNINGS+1)); fi
if [ "$UNUSED_IMPORTS" -gt 0 ]; then WARNINGS=$((WARNINGS+1)); fi
if [ "$MOD_CRITICAL" -gt 0 ]; then CRITICAL=$((CRITICAL+1)); fi
if [ "$MOD_WARNINGS" -gt 0 ]; then WARNINGS=$((WARNINGS+1)); fi

if [ "$CRITICAL" -eq 0 ] && [ "$WARNINGS" -eq 0 ]; then
    echo "✅ All checks passed! Code is ready to commit."
    exit 0
elif [ "$CRITICAL" -eq 0 ]; then
    echo "⚠️  Code has $WARNINGS warning(s). Review and fix before committing."
    exit 0
else
    echo "❌ Code has $CRITICAL critical issue(s)! Fix before committing."
    exit 1
fi

