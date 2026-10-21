package org.cef.browser;

import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A windowless Chromium browser that is its own {@link CefRenderHandler} and owns no AWT surface.
 *
 * <p>JCEF's own {@code CefBrowserOsr} draws through a JOGL {@code GLCanvas} and is package-private.
 * This class sits in the same package (the JCEF jar is not sealed, and on the class path packages are
 * not split) so it can extend {@code CefBrowser_N} directly: Chromium paints into a BGRA buffer, the
 * sink gets it raw, and nothing touches OpenGL or the AWT peer classes. It is Java because Kotlin
 * refuses a public subclass of a package-private class.
 *
 * <p>{@code viewWidth}×{@code viewHeight} is the page's size in DIPs; {@code scale} is the device
 * scale factor Chromium is told, so the painted buffer is (view × scale) physical pixels.
 */
public class CefBrowserHeadless extends CefBrowser_N implements CefRenderHandler {
    private final HeadlessSink sink;
    private final Component eventSource = new Canvas();
    private final CopyOnWriteArrayList<Consumer<CefPaintEvent>> paintListeners = new CopyOnWriteArrayList<>();
    private volatile int viewWidth;
    private volatile int viewHeight;
    private volatile double scale;

    public CefBrowserHeadless(CefClient client, String url, CefBrowserSettings settings,
                              int viewWidth, int viewHeight, double scale, HeadlessSink sink) {
        super(client, url, null, null, null, settings);
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.scale = scale;
        this.sink = sink;
    }

    public int getViewWidth() { return viewWidth; }

    public int getViewHeight() { return viewHeight; }

    /** The source every forwarded AWT event names. Never shown; JCEF reads only coordinates and flags. */
    public Component getEventSource() { return eventSource; }

    @Override
    public void createImmediately() {
        if (getNativeRef("CefBrowser") == 0) {
            createBrowser(getClient(), 0L, getUrl(), true, false, null, getRequestContext());
        }
    }

    @Override
    public Component getUIComponent() { return eventSource; }

    @Override
    public CefRenderHandler getRenderHandler() { return this; }

    @Override
    protected CefBrowser_N createDevToolsBrowser(CefClient client, String url, CefRequestContext context,
                                                 CefBrowser_N parent, Point inspectAt) {
        return null;
    }

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("use the paint stream"));
    }

    public double getScale() { return scale; }

    /**
     * Changes the page's size in DIPs and its device scale; Chromium asks {@link #getViewRect} and
     * {@link #getScreenInfo} again and repaints.
     */
    public void resize(int width, int height, double scale) {
        viewWidth = width;
        viewHeight = height;
        this.scale = scale;
        wasResized(width, height);
    }

    public void mouse(MouseEvent event) { sendMouseEvent(event); }

    public void wheel(MouseWheelEvent event) { sendMouseWheelEvent(event); }

    public void key(KeyEvent event) { sendKeyEvent(event); }

    // --- CefRenderHandler ------------------------------------------------------------------

    @Override
    public Rectangle getViewRect(CefBrowser browser) { return new Rectangle(0, 0, viewWidth, viewHeight); }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo info) {
        Rectangle rect = new Rectangle(0, 0, viewWidth, viewHeight);
        info.Set(scale, 32, 8, false, rect, rect);
        return true;
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) { return new Point(viewPoint); }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) { sink.onPopupShow(show); }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) { sink.onPopupSize(size); }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer,
                        int width, int height) {
        try {
            sink.onPaint(popup, dirtyRects, buffer, width, height);
        } catch (RuntimeException | Error e) {
            // An exception here would unwind into Chromium's paint call through JNI, which reports
            // only its thread name; the sink reports it whole instead.
            sink.onPaintFailed(e);
        }
        if (!paintListeners.isEmpty()) {
            CefPaintEvent event = new CefPaintEvent(this, popup, dirtyRects, buffer, width, height);
            for (Consumer<CefPaintEvent> listener : paintListeners) listener.accept(event);
        }
    }

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) { paintListeners.add(listener); }

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
        paintListeners.clear();
        paintListeners.add(listener);
    }

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) { paintListeners.remove(listener); }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        sink.onCursorChange(cursorType);
        return true;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) { return false; }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) { }
}
