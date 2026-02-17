# Building Installers for ChurchPresenter

This guide explains how to build DMG (macOS), EXE (Windows), and other installers for ChurchPresenter.

## Prerequisites

### For macOS (DMG)
- macOS operating system
- Xcode Command Line Tools installed: `xcode-select --install`
- Java JDK 17 or higher

### For Windows (EXE/MSI)
- Windows operating system
- WiX Toolset v3.x (for MSI) - Download from: https://wixtoolset.org/
- Inno Setup (for EXE) - Download from: https://jrsoftware.org/isinfo.php
- Java JDK 17 or higher

### For Linux (DEB)
- Linux operating system
- Java JDK 17 or higher

## Building Installers

### Build All Installers (on current platform)
```bash
./gradlew packageDistributionForCurrentOS
```

### Build DMG (macOS only)
```bash
./gradlew packageDmg
```
The DMG file will be created in:
`composeApp/build/compose/binaries/main/dmg/`

### Build EXE (Windows only)
```bash
./gradlew packageExe
```
The EXE installer will be created in:
`composeApp/build/compose/binaries/main/exe/`

### Build MSI (Windows only)
```bash
./gradlew packageMsi
```
The MSI installer will be created in:
`composeApp/build/compose/binaries/main/msi/`

### Build DEB (Linux only)
```bash
./gradlew packageDeb
```
The DEB package will be created in:
`composeApp/build/compose/binaries/main/deb/`

## Creating Application Icons

To customize your application icon, you need to create icon files in the appropriate formats:

### macOS Icon (.icns)
1. Create a 1024x1024 PNG icon
2. Use Icon Composer or `iconutil` to convert to .icns format
3. Place at: `composeApp/src/jvmMain/resources/icon.icns`

Example using iconutil:
```bash
# Create iconset folder
mkdir MyIcon.iconset

# Create different sizes (16, 32, 64, 128, 256, 512, 1024)
sips -z 16 16     icon1024.png --out MyIcon.iconset/icon_16x16.png
sips -z 32 32     icon1024.png --out MyIcon.iconset/icon_16x16@2x.png
sips -z 32 32     icon1024.png --out MyIcon.iconset/icon_32x32.png
sips -z 64 64     icon1024.png --out MyIcon.iconset/icon_32x32@2x.png
sips -z 128 128   icon1024.png --out MyIcon.iconset/icon_128x128.png
sips -z 256 256   icon1024.png --out MyIcon.iconset/icon_128x128@2x.png
sips -z 256 256   icon1024.png --out MyIcon.iconset/icon_256x256.png
sips -z 512 512   icon1024.png --out MyIcon.iconset/icon_256x256@2x.png
sips -z 512 512   icon1024.png --out MyIcon.iconset/icon_512x512.png
sips -z 1024 1024 icon1024.png --out MyIcon.iconset/icon_512x512@2x.png

# Convert to .icns
iconutil -c icns MyIcon.iconset
```

### Windows Icon (.ico)
1. Create icons in multiple sizes: 16x16, 32x32, 48x48, 64x64, 128x128, 256x256
2. Use a tool like GIMP, ImageMagick, or online converters to create .ico file
3. Place at: `composeApp/src/jvmMain/resources/icon.ico`

Example using ImageMagick:
```bash
convert icon.png -define icon:auto-resize=256,128,64,48,32,16 icon.ico
```

### Linux Icon (.png)
1. Create a 512x512 or 256x256 PNG icon
2. Place at: `composeApp/src/jvmMain/resources/icon.png`

## Distribution

After building, you can distribute:
- **DMG**: Users drag the app to Applications folder
- **EXE**: Users run the installer wizard
- **MSI**: Users run the Windows Installer package
- **DEB**: Users install with `sudo dpkg -i package.deb`

## Troubleshooting

### "Icon file not found" error
If you see icon file errors during build, you can comment out the icon lines in `composeApp/build.gradle.kts` or create placeholder icon files.

### DMG build fails on macOS
Ensure Xcode Command Line Tools are installed:
```bash
xcode-select --install
```

### EXE/MSI build fails on Windows
- For MSI: Install WiX Toolset v3.x and add it to PATH
- For EXE: Install Inno Setup and add it to PATH

### Permission denied errors
On macOS/Linux, ensure the gradlew script is executable:
```bash
chmod +x gradlew
```

## Clean Build

To clean all build artifacts before rebuilding:
```bash
./gradlew clean
```

## Advanced Configuration

See `composeApp/build.gradle.kts` for additional configuration options:
- Package version
- App description
- Copyright information
- Vendor name
- Bundle IDs
- Installation options

## Notes

- **DMG** can only be built on macOS
- **EXE/MSI** can only be built on Windows
- **DEB** can only be built on Linux
- Each platform-specific build requires the respective operating system
- The build process will bundle the appropriate JRE with your application

