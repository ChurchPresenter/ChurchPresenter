package org.cef.browser;

import java.awt.Rectangle;
import java.nio.ByteBuffer;

/** What a windowless browser hands out: every paint (main view or popup), popup placement, cursor changes. */
public interface HeadlessSink {
    void onPaint(boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height);

    void onPopupShow(boolean show);

    void onPopupSize(Rectangle rect);

    void onCursorChange(int cursorType);

    /** {@link #onPaint} threw; called on the same thread, with what it threw. */
    void onPaintFailed(Throwable failure);
}
