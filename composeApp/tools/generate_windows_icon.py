#!/usr/bin/env python3
"""Builds the Windows icon, and the window-icon frames, from the macOS iconset.

The Windows icon used to be its own design: a bare church with a thin light outline. At 24 and 32
px -- what the taskbar draws at 100% scaling, one image pixel per screen pixel -- that outline
turned to grey blur, the steeple smeared and the side windows vanished. The macOS design (the
church on a flat cream tile, no outline) keeps clean edges at those sizes, so Windows now uses it
too.

Frames the iconset holds at exactly the right size are copied as they are; the others are
downscaled from the 1024 px master with Lanczos, never from another small frame.

Writes:
  composeApp/src/jvmMain/appResources/windows/icon.ico   -- the installer, taskbar and Start menu
  composeApp/src/jvmMain/resources/app-icon/icon-<n>.png -- what the app's own windows hand to
                                                           Window.setIconImages (title bar, Alt+Tab)

Run from the repository root after changing the macOS iconset:
  python3 composeApp/tools/generate_windows_icon.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent
ICONSET = ROOT / "composeApp/src/jvmMain/appResources/macos/icon.iconset"
ICO = ROOT / "composeApp/src/jvmMain/appResources/windows/icon.ico"
WINDOW_FRAMES = ROOT / "composeApp/src/jvmMain/resources/app-icon"

# Every size Windows asks an .ico for across 100-300% scaling: the shell's small and large icons,
# the taskbar, Alt+Tab and Explorer's views.
ICO_SIZES = [16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 128, 256]

# What a window hands to setIconImages: Windows picks the nearest for the title bar and Alt+Tab.
WINDOW_SIZES = [16, 20, 24, 32, 40, 48, 64, 128, 256]

# The iconset's frames that already are one of these sizes, drawn for it.
EXACT = {
    16: "icon_16x16.png",
    32: "icon_32x32.png",
    64: "icon_32x32@2x.png",
    128: "icon_128x128.png",
    256: "icon_256x256.png",
}
MASTER = "icon_512x512@2x.png"


def frame(size: int) -> Image.Image:
    name = EXACT.get(size)
    if name is not None:
        image = Image.open(ICONSET / name).convert("RGBA")
        assert image.size == (size, size), f"{name} is {image.size}, not {size}"
        return image
    return Image.open(ICONSET / MASTER).convert("RGBA").resize((size, size), Image.LANCZOS)


def main() -> None:
    frames = {size: frame(size) for size in sorted(set(ICO_SIZES) | set(WINDOW_SIZES))}

    largest = frames[max(ICO_SIZES)]
    largest.save(
        ICO,
        format="ICO",
        sizes=[(s, s) for s in ICO_SIZES],
        append_images=[frames[s] for s in ICO_SIZES if s != max(ICO_SIZES)],
    )

    WINDOW_FRAMES.mkdir(parents=True, exist_ok=True)
    for size in WINDOW_SIZES:
        frames[size].save(WINDOW_FRAMES / f"icon-{size}.png", optimize=True)

    written = Image.open(ICO)
    print(f"{ICO.relative_to(ROOT)}: {sorted(written.info['sizes'])}")
    print(f"{WINDOW_FRAMES.relative_to(ROOT)}: {len(WINDOW_SIZES)} frames")


if __name__ == "__main__":
    main()
