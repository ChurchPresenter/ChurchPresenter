package org.churchpresenter.sharedui.screenshot;

import androidx.compose.ui.text.font.FontFamily;
import androidx.compose.ui.text.font.GenericFontFamily;
import androidx.compose.ui.text.font.FontFamilyResolver_sikioKt;
import androidx.compose.ui.text.font.SkiaFontLoader;
import androidx.compose.ui.text.platform.FontCache;
import androidx.compose.ui.text.platform.PlatformFont_skikoKt;
import org.jetbrains.skia.FontMgr;

/**
 * A Compose font resolver whose font collection asks {@code fonts} for a family before the system,
 * and the name Compose asks the system for a generic family by on this platform.
 *
 * <p>Written in Java because Compose marks the font cache and its loader {@code internal}: they are
 * public in the bytecode, and only the Kotlin compiler refuses them. A Compose release that changes
 * them breaks this file's compile, and {@code PinnedFontsTest} fails if text inside
 * {@code PinnedFonts} stops being drawn in the bundled font.
 */
final class ComposeFontCollection {
    private ComposeFontCollection() {
    }

    static FontFamily.Resolver resolverLookingFirstIn(FontMgr fonts) {
        FontCache cache = new FontCache();
        // The collection belongs to the cache, so the resolver made over the same cache uses it.
        // The dynamic manager is the first one a Skia paragraph asks for a family.
        new SkiaFontLoader(cache).getFontCollection().setDynamicFontManager(fonts);
        return FontFamilyResolver_sikioKt.createFontFamilyResolver(cache);
    }

    /** The first name Compose looks {@code family} up by here: {@code .AppleSystemUIFont} on macOS. */
    static String systemName(GenericFontFamily family) {
        return PlatformFont_skikoKt.getAliases(family).get(0);
    }
}
