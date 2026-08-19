package companionsatellite

/**
 * The surface a client registers with Companion: how many buttons it has, how big their bitmaps
 * are, and where on Companion's real page grid its top-left button sits.
 *
 * One value rather than seven parameters threaded through connect, the reconnect loop, the line
 * handler and registration — they always travelled together and always described the same surface.
 */
data class SurfaceSpec(
    val deviceId: String,
    val rows: Int,
    val columns: Int,
    val bitmapSize: Int,
    /**
     * Top-left corner of Companion's real page grid that this device's controls map onto — 0/0
     * shows the page's own top-left corner (the only option the legacy KEYS_TOTAL/KEYS_PER_ROW
     * registration ever supported); any other value shows an arbitrary sub-rectangle instead,
     * which is why registration always uses LAYOUT_MANIFEST now.
     */
    val startRow: Int = 0,
    val startColumn: Int = 0,
    val productName: String = "ChurchPresenter",
) {
    val buttonCount: Int get() = rows * columns
}
