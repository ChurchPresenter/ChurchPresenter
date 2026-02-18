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

# Count Material 2 imports
MATERIAL2=$(grep -r 'import androidx.compose.material\.[^3]' --include='*.kt' composeApp/src/ 2>/dev/null | wc -l | tr -d ' ')
echo "🎨 Material 2 imports: $MATERIAL2"
if [ "$MATERIAL2" -gt 0 ]; then
    echo "   ❌ FAIL - Should be 0"
else
    echo "   ✅ PASS"
fi

# Count debug prints
PRINTS=$(grep -rE '(println|print\()' --include='*.kt' composeApp/src/jvmMain/kotlin/ 2>/dev/null | wc -l | tr -d ' ')
echo "🐛 Debug print statements: $PRINTS"
if [ "$PRINTS" -gt 0 ]; then
    echo "   ⚠️  WARN - Should be 0"
else
    echo "   ✅ PASS"
fi

# Count fully qualified type names
QUALIFIED=$(grep -r 'androidx\.compose\.[a-z]*\.[a-zA-Z]*\.[A-Z]' --include='*.kt' composeApp/src/ 2>/dev/null | wc -l | tr -d ' ')
echo "📝 Fully qualified type names: $QUALIFIED"
if [ "$QUALIFIED" -gt 0 ]; then
    echo "   ⚠️  WARN - Should be 0"
else
    echo "   ✅ PASS"
fi

echo ""
echo "Building project to check for warnings..."
echo ""

# Build and check for unused code
./gradlew compileKotlinJvm --no-daemon 2>&1 > /tmp/build_output.txt

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

