# Quick Start: Building ChurchPresenter Installers

## TL;DR - Build Commands

### On macOS - Build DMG
```bash
./gradlew packageReleaseDmg
```
Output location: `composeApp/build/compose/binaries/main-release/dmg/`

### On Windows - Build EXE
```bash
./gradlew packageReleaseExe
```
Output location: `composeApp/build/compose/binaries/main-release/exe/`

### On Windows - Build MSI
```bash
./gradlew packageReleaseMsi
```
Output location: `composeApp/build/compose/binaries/main-release/msi/`

### On Linux - Build DEB
```bash
./gradlew packageReleaseDeb
```
Output location: `composeApp/build/compose/binaries/main-release/deb/`

### Build for Current Platform (Auto-detect)
```bash
./gradlew packageReleaseDistributionForCurrentOS
```
This will build the appropriate installer for your current operating system.

## What You Get

- **DMG (macOS)**: Drag-and-drop installer with bundled JRE
- **EXE (Windows)**: Setup wizard installer with bundled JRE  
- **MSI (Windows)**: Windows Installer package with bundled JRE
- **DEB (Linux)**: Debian package with bundled JRE

## Testing Locally (without installer)

To test the app without building an installer:
```bash
./gradlew run
```

## Next Steps

1. **Build your installer** using one of the commands above
2. **Test the installer** on a clean machine
3. **Distribute** to your users

For more detailed instructions including icon creation, see [BUILD_INSTALLERS.md](BUILD_INSTALLERS.md)

