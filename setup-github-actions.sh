#!/bin/bash

# Quick Start: Build Windows EXE via GitHub Actions
# This script adds the GitHub Actions workflow and pushes it to trigger a build

set -e

echo "================================================"
echo "  ChurchPresenter - GitHub Actions Setup"
echo "================================================"
echo ""

# Check if we're in the right directory
if [ ! -f "build.gradle.kts" ]; then
    echo "❌ Error: Not in ChurchPresenter root directory"
    echo "Please run this script from: /Users/andreichernyshev/Documents/GitHub/ChurchPresenter"
    exit 1
fi

echo "✅ In correct directory"
echo ""

# Check if .github/workflows exists
if [ ! -f ".github/workflows/build-installers.yml" ]; then
    echo "❌ Error: GitHub Actions workflow not found"
    echo "Expected: .github/workflows/build-installers.yml"
    exit 1
fi

echo "✅ GitHub Actions workflow found"
echo ""

# Check git status
echo "📋 Current git status:"
git status --short
echo ""

# Add files
echo "📦 Adding files to git..."
git add .github/
git add WINDOWS_BUILD_GUIDE.md
git add MEMORY_CONFIGURATION.md
git add DMG_BUILD_SUCCESS.md
git add BUILD_INSTALLERS.md
git add QUICK_START_INSTALLERS.md 2>/dev/null || true

echo "✅ Files staged"
echo ""

# Show what will be committed
echo "📋 Files to be committed:"
git status --short
echo ""

# Commit
read -p "Commit these files? (y/n) " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "💾 Committing..."
    git commit -m "Add GitHub Actions workflow for cross-platform installer builds

- Windows EXE and MSI
- macOS DMG
- Linux DEB
- Automatic builds on push
- Release automation on tags
- Fixes OutOfMemoryError with 10GB heap"

    echo "✅ Committed"
    echo ""

    # Push
    read -p "Push to GitHub to trigger build? (y/n) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🚀 Pushing to GitHub..."
        git push

        echo ""
        echo "================================================"
        echo "  ✅ SUCCESS!"
        echo "================================================"
        echo ""
        echo "GitHub Actions will now build your installers!"
        echo ""
        echo "To watch the build:"
        echo "1. Go to your GitHub repository"
        echo "2. Click the 'Actions' tab"
        echo "3. Watch the 'Build Installers' workflow"
        echo ""
        echo "When complete (10-20 minutes):"
        echo "1. Click on the workflow run"
        echo "2. Scroll to 'Artifacts'"
        echo "3. Download:"
        echo "   - windows-exe-installer (Windows EXE)"
        echo "   - windows-msi-installer (Windows MSI)"
        echo "   - macos-dmg-installer (macOS DMG)"
        echo "   - linux-deb-package (Linux DEB)"
        echo ""
        echo "🎉 Your Windows EXE will be ready soon!"

        # Get the repository URL
        REPO_URL=$(git config --get remote.origin.url | sed 's/\.git$//')
        if [[ $REPO_URL == git@* ]]; then
            REPO_URL=$(echo $REPO_URL | sed 's/git@github.com:/https:\/\/github.com\//')
        fi

        echo ""
        echo "📍 Actions URL: ${REPO_URL}/actions"
        echo ""

        # Try to open in browser
        if command -v open &> /dev/null; then
            read -p "Open GitHub Actions in browser? (y/n) " -n 1 -r
            echo ""
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                open "${REPO_URL}/actions"
            fi
        fi
    else
        echo "⏸️  Not pushed. You can push later with: git push"
    fi
else
    echo "⏸️  Not committed. You can commit later with:"
    echo "   git commit -m \"Add GitHub Actions for Windows builds\""
fi

echo ""
echo "================================================"
echo "Done!"
echo "================================================"

