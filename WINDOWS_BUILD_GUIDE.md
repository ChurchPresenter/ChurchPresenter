# Building Windows EXE with GitHub Actions

## Overview

Since you're on macOS and cannot build Windows executables locally, I've set up **GitHub Actions** to automatically build Windows EXE and MSI installers for you using Windows runners in the cloud.

## What Was Created

### GitHub Actions Workflow
**File:** `.github/workflows/build-installers.yml`

This workflow automatically builds:
- ✅ **Windows EXE** (on Windows runner)
- ✅ **Windows MSI** (on Windows runner)
- ✅ **macOS DMG** (on macOS runner)
- ✅ **Linux DEB** (on Linux runner)

## How to Use

### Option 1: Push to GitHub (Automatic Build)

1. **Commit and push your code:**
   ```bash
   cd /Users/andreichernyshev/Documents/GitHub/ChurchPresenter
   git add .
   git commit -m "Add GitHub Actions for building installers"
   git push origin main
   ```
   (Replace `main` with `master` if that's your default branch)

2. **Watch the build:**
   - Go to your GitHub repository
   - Click on the "Actions" tab
   - You'll see the workflow running
   - Wait for it to complete (usually 10-20 minutes)

3. **Download your installers:**
   - Click on the completed workflow run
   - Scroll down to "Artifacts"
   - Download:
     - `windows-exe-installer` - Windows EXE
     - `windows-msi-installer` - Windows MSI
     - `macos-dmg-installer` - macOS DMG
     - `linux-deb-package` - Linux DEB

### Option 2: Manual Trigger

1. **Go to GitHub Actions:**
   - Navigate to your repository on GitHub
   - Click "Actions" tab
   - Select "Build Installers" workflow
   - Click "Run workflow" button
   - Select the branch
   - Click "Run workflow"

2. **Download artifacts** (same as Option 1, step 3)

### Option 3: Create a Release (Automatic)

To automatically create a GitHub Release with all installers:

1. **Create and push a version tag:**
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```

2. **GitHub Actions will:**
   - Build all installers (EXE, MSI, DMG, DEB)
   - Create a GitHub Release
   - Attach all installers to the release
   - Generate release notes

3. **Users can download from:**
   - Go to your repository
   - Click "Releases"
   - Download the installer for their platform

## Workflow Triggers

The workflow runs automatically when:
- ✅ You push to `main` or `master` branch
- ✅ You create a pull request
- ✅ You push a tag starting with `v` (e.g., `v1.0.0`)
- ✅ You manually trigger it from GitHub Actions tab

## Build Configuration

### Memory Settings
The workflow uses the same memory settings that work locally:
```yaml
env:
  GRADLE_OPTS: "-Xmx10240M -XX:MaxMetaspaceSize=2048M"
```

### ProGuard
ProGuard is disabled (as configured in `build.gradle.kts`) to prevent OutOfMemoryError.

### Build Time
Approximate build times:
- Windows EXE: 15-20 minutes
- Windows MSI: 15-20 minutes (or skipped if tools unavailable)
- macOS DMG: 10-15 minutes
- Linux DEB: 10-15 minutes

**Total:** ~20 minutes for all platforms in parallel

## GitHub Repository Setup

### Prerequisites

1. **Initialize Git (if not already done):**
   ```bash
   cd /Users/andreichernyshev/Documents/GitHub/ChurchPresenter
   git init
   git add .
   git commit -m "Initial commit"
   ```

2. **Create GitHub repository:**
   - Go to https://github.com/new
   - Create a new repository named "ChurchPresenter"
   - Don't initialize with README (you already have files)

3. **Connect and push:**
   ```bash
   git remote add origin https://github.com/YOUR_USERNAME/ChurchPresenter.git
   git branch -M main
   git push -u origin main
   ```

### Enable GitHub Actions

GitHub Actions is enabled by default for most repositories. If not:
1. Go to repository Settings
2. Click "Actions" → "General"
3. Enable "Allow all actions and reusable workflows"

## Artifacts Retention

By default, GitHub keeps workflow artifacts for **90 days**. After that, they're automatically deleted. Create releases for permanent storage.

## What Gets Built

### Windows EXE
- **File:** `ChurchPresenter-1.0.0.exe`
- **Size:** ~80-100 MB
- **Includes:** JRE bundled
- **Installer:** Inno Setup installer with wizard

### Windows MSI
- **File:** `ChurchPresenter-1.0.0.msi`
- **Size:** ~80-100 MB
- **Includes:** JRE bundled
- **Installer:** Windows Installer package
- **Note:** May fail if WiX Toolset not available (EXE is primary)

### macOS DMG
- **File:** `ChurchPresenter-1.0.0.dmg`
- **Size:** ~81 MB
- **Includes:** JRE bundled
- **Installer:** Drag-and-drop DMG

### Linux DEB
- **File:** `ChurchPresenter-1.0.0.deb`
- **Size:** ~80-100 MB
- **Includes:** JRE bundled
- **Installer:** Debian package

## Quick Start Guide

### First Time Setup

```bash
# 1. Navigate to your project
cd /Users/andreichernyshev/Documents/GitHub/ChurchPresenter

# 2. Initialize git (if needed)
git init

# 3. Add all files
git add .

# 4. Commit
git commit -m "Add GitHub Actions for cross-platform builds"

# 5. Create GitHub repository and push
# (Follow GitHub's instructions after creating the repo)
git remote add origin https://github.com/YOUR_USERNAME/ChurchPresenter.git
git push -u origin main
```

### Build Windows EXE

```bash
# Option A: Push to trigger automatic build
git add .
git commit -m "Update"
git push

# Option B: Create a release
git tag v1.0.0
git push origin v1.0.0

# Then download from GitHub Actions artifacts or Releases
```

## Alternative: Local Windows Build

If you have access to a Windows machine:

### On Windows
```bash
# Clone your repository
git clone https://github.com/YOUR_USERNAME/ChurchPresenter.git
cd ChurchPresenter

# Build EXE
gradlew.bat packageReleaseExe

# Build MSI
gradlew.bat packageReleaseMsi

# Find installers in:
# composeApp\build\compose\binaries\main-release\exe\
# composeApp\build\compose\binaries\main-release\msi\
```

## Troubleshooting

### Build Fails with OutOfMemoryError

The workflow is already configured with 10GB memory. If it still fails:

1. **Increase memory in workflow:**
   ```yaml
   env:
     GRADLE_OPTS: "-Xmx12288M -XX:MaxMetaspaceSize=2048M"
   ```

2. **Ensure ProGuard is disabled** in `build.gradle.kts`

### Workflow Not Running

1. Check Actions are enabled in repository settings
2. Verify the workflow file is in `.github/workflows/`
3. Check workflow syntax at: https://www.yamllint.com/

### Artifacts Not Available

1. Check the workflow completed successfully
2. Artifacts expire after 90 days - create releases for permanent storage
3. Verify the build step succeeded (green checkmark)

### MSI Build Fails

This is normal - MSI requires WiX Toolset which may not be available. The workflow is configured to continue even if MSI fails. The EXE installer is the primary Windows installer.

## Cost

GitHub Actions provides:
- **Free for public repositories:** Unlimited minutes
- **Free for private repositories:** 2,000 minutes/month
- Windows runners use 2x minutes (so 1 minute = 2 minutes consumed)

A typical build uses ~40 minutes (20 minutes × 2x for Windows) per run.

## Benefits of This Approach

✅ **No Windows machine needed** - Build from your Mac  
✅ **All platforms in parallel** - Save time  
✅ **Automatic builds** - Push and forget  
✅ **Release automation** - Tag and release automatically  
✅ **Version control** - All builds tracked in Git  
✅ **Free for public repos** - No cost if repository is public  
✅ **Professional workflow** - Industry standard approach  

## Next Steps

1. **Push your code to GitHub** (if not already done)
2. **Watch the Actions tab** for the first build
3. **Download the Windows EXE** from artifacts
4. **Test the installer** on a Windows machine
5. **Create a release** for permanent distribution

## Example Release Command

```bash
# Create version 1.0.0 release
git tag -a v1.0.0 -m "ChurchPresenter v1.0.0 - Initial release"
git push origin v1.0.0

# GitHub Actions will:
# - Build all installers
# - Create GitHub Release
# - Attach all files to release
# - Generate release notes
```

Users can then download from: `https://github.com/YOUR_USERNAME/ChurchPresenter/releases`

---

## Summary

✅ **GitHub Actions workflow created**  
✅ **Builds Windows EXE automatically**  
✅ **Also builds MSI, DMG, and DEB**  
✅ **Runs on every push or manual trigger**  
✅ **Creates releases on version tags**  
✅ **No Windows machine required**  

**You can now build Windows executables from your Mac!**

Just push your code to GitHub and download the artifacts from the Actions tab.

