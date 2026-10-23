package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.geometry.Rect
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The storyboard's frame: 1920×1080 at 30fps. Every size below is in those pixels.
internal const val OUT_W = 1920
internal const val OUT_H = 1080

private val BG = Color(0x09, 0x09, 0x0B)
private val ACCENT = Color(0x86, 0xEF, 0xAC)
private val MUTED = Color(0xA1, 0xA1, 0xAA)
private val FAINT = Color(0x71, 0x71, 0x7A)
private val INK = Color(0xFA, 0xFA, 0xFA)
private val PANEL = Color(0x18, 0x18, 0x1B)
private val TITLEBAR = Color(0x26, 0x26, 0x2E)
private val LIVE_RED = Color(0xDC, 0x26, 0x26)

/** The operator window as Establish frames it, and as the projector scene shrinks it. */
private val FULL_DST = Rect(240f, 96f, 1680f, 996f)
private val OPERATOR_DST = Rect(56f, 182f, 1208f, 902f)

/** The projector panel of the "Demo with projector" scene. */
private val SCREEN = Rect(1324f, 346f, 1864f, 650f)

private val TABS = listOf(
    "Bible", "Songs", "Pictures", "Presentation", "Media", "Lower Third", "Announcements", "Web", "Canvas", "Q&A",
)

/**
 * Which part of the app ([src], in app pixels) is shown where on the frame ([dst]), and how much of the
 * window chrome and the projector panel is showing.
 */
internal data class Camera(val src: Rect, val dst: Rect, val chrome: Float, val projector: Float) {
    val k get() = dst.width / src.width

    fun map(x: Float, y: Float) = Pt(dst.left + (x - src.left) * k, dst.top + (y - src.top) * k)

    fun map(r: Rect): Rect {
        val a = map(r.left, r.top)
        val b = map(r.right, r.bottom)
        return Rect(a.x, a.y, b.x, b.y)
    }

    fun lerp(to: Camera, t: Float) =
        Camera(lerp(src, to.src, t), lerp(dst, to.dst, t), lerp(chrome, to.chrome, t), lerp(projector, to.projector, t))

    fun near(o: Camera): Boolean {
        val a = map(0f, 0f)
        val b = o.map(0f, 0f)
        return abs(a.x - b.x) + abs(a.y - b.y) < 4f && abs(k - o.k) < 0.002f && abs(projector - o.projector) < 0.01f
    }

    companion object {
        /** The whole window, framed as in Establish. */
        fun full(appW: Float, appH: Float) = Camera(Rect(0f, 0f, appW, appH), FULL_DST, 1f, 0f)

        /** The operator window at 60%, the projector beside it. */
        fun projector(appW: Float, appH: Float) = Camera(Rect(0f, 0f, appW, appH), OPERATOR_DST, 1f, 1f)

        /** [zoom]× the Establish scale, centered on [target] with room below it for the tooltip. */
        fun zoom(target: Rect, zoom: Float, appW: Float, appH: Float): Camera {
            val fit = min(OUT_W * 0.8f / target.width, OUT_H * 0.45f / target.height) / (FULL_DST.width / appW)
            val k = FULL_DST.width / appW * min(zoom, max(1f, fit))
            val w = OUT_W / k
            val h = OUT_H / k
            val x = (target.center.x - w / 2).coerceIn(-0.25f * w, max(-0.25f * w, appW - 0.75f * w))
            val y = (target.center.y - h * 0.38f).coerceIn(-0.25f * h, max(-0.25f * h, appH - 0.75f * h))
            return Camera(Rect(x, y, x + w, y + h), Rect(0f, 0f, OUT_W.toFloat(), OUT_H.toFloat()), 0f, 0f)
        }
    }
}

/** A highlighted control: its outline, the dim around it, and its tooltip. */
internal class Focus(val rect: Rect, val step: Int?, val title: String, val body: String, val compact: Boolean)

/** Everything a frame shows, mutated by the tour as it plays and drawn by [DemoStage]. */
internal class Scene(var app: BufferedImage, var projector: BufferedImage, var camera: Camera) {
    var appBefore: BufferedImage? = null
    var appFade = 1f
    var projectorBefore: BufferedImage? = null
    var projectorFade = 1f
    var focus: Focus? = null
    var focusAge = 0
    var cursor: Pt? = null
    var pressed = false
    var ripple: Float? = null
    var pointerTag: String? = null
    var keycap: String? = null
    var caption: String? = null
    var captionAge = 0
    var live = false
    var liveAge = 1_000
    var arrow = 0f
    var header: Pair<String, String> = "" to ""
    var step = 0
    var steps = 0
}

/** Draws the storyboard's components. Holds no timeline of its own: ages in [Scene] drive its animations. */
internal class DemoStage(private val appW: Float, private val appH: Float) {
    private var base: BufferedImage? = null
    private var baseKey: List<Any?> = emptyList()

    fun render(s: Scene): BufferedImage {
        val img = BufferedImage(OUT_W, OUT_H, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.drawImage(baseLayer(s), 0, 0, null)
        g.quality()
        s.focus?.let { focus(g, s, it) }
        s.keycap?.let { keycap(g, s, it) }
        s.cursor?.let { cursor(g, s, s.camera.map(it.x, it.y)) }
        s.caption?.let { caption(g, it, ease(min(1f, s.captionAge / 6f))) }
        if (s.steps > 0 && s.step > 0) progress(g, s.step, s.steps)
        g.dispose()
        return img
    }

    fun intro(tab: String, subtitle: String, age: Int): BufferedImage = card { g ->
        glow(g, 1340f, 480f, 360f, 0.22f)
        val t = ease(min(1f, age / 18f))
        val fade = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t)
        g.composite = fade
        val rise = (1 - t) * 24f
        val pill = DemoFonts.sans(600, 28f, 0.16f)
        g.font = pill
        val label = "CHURCH PRESENTER · TUTORIAL"
        val pw = g.fontMetrics.stringWidth(label) + 56f
        var y = 363f + rise
        g.color = Color(255, 255, 255, 36)
        g.stroke = BasicStroke(1f)
        g.draw(RoundRectangle2D.Float(160f, y, pw, 58f, 58f, 58f))
        g.color = ACCENT
        g.drawString(label, 188f, y + 39f)
        y += 58f + 40f
        title(g, tab, 168f, 160f, y + 140f)
        y += 160f + 40f
        g.font = DemoFonts.sans(400, 40f)
        g.color = MUTED
        g.drawString(subtitle, 160f, y + 42f)
        val chips = ease(((age - 14) / 14f).coerceIn(0f, 1f))
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, chips)
        tabChips(g, tab, 160f, OUT_H - 110f - 50f)
    }

    fun outro(tab: String, age: Int): BufferedImage = card { g ->
        glow(g, 960f, 540f, 360f, 0.18f)
        val t = ease(min(1f, age / 18f))
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t)
        val rise = (1 - t) * 24f
        val w = titleWidth(g, tab, 150f)
        title(g, tab, 150f, 960f - w / 2, 530f + rise)
        g.font = DemoFonts.sans(400, 34f)
        g.color = MUTED
        centered(g, "churchpresenter.org", 960f, 630f + rise)
    }

    // ── The app, its window and the projector ─────────────────────────────

    private fun baseLayer(s: Scene): BufferedImage {
        val key = listOf(
            s.app, s.appBefore, s.appFade, s.projector, s.projectorBefore, s.projectorFade,
            s.camera, s.live, s.liveAge.coerceAtMost(30), s.arrow, s.header,
        )
        base?.takeIf { key == baseKey }?.let { return it }
        val img = BufferedImage(OUT_W, OUT_H, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.quality()
        g.color = BG
        g.fillRect(0, 0, OUT_W, OUT_H)
        val cam = s.camera
        if (cam.projector > 0.01f) projectorPanel(g, s, cam.projector)
        window(g, s, cam)
        g.dispose()
        base = img
        baseKey = key
        return img
    }

    private fun window(g: Graphics2D, s: Scene, cam: Camera) {
        val r = cam.map(Rect(0f, 0f, appW, appH))
        val scale = r.width / FULL_DST.width
        val bar = 36f * scale * cam.chrome
        val radius = 16f * max(scale, 0.6f)
        val frame = RoundRectangle2D.Float(r.left, r.top - bar, r.width, r.height + bar, radius, radius)
        // A soft drop shadow: 0 40px 100px -20px rgba(0,0,0,.8), as a few widening layers.
        softShadow(g, Rect(r.left, r.top - bar, r.right, r.bottom), Shadow(30f * scale, 10f * scale, radius, 22, 6))
        val old = g.clip
        g.clip(frame)
        if (bar > 0.5f) {
            g.color = TITLEBAR
            g.fill(Rectangle2D.Float(r.left, r.top - bar, r.width, bar))
            val dot = 12f * scale
            listOf(Color(0xFF, 0x5F, 0x57), Color(0xFE, 0xBC, 0x2E), Color(0x28, 0xC8, 0x40)).forEachIndexed { i, c ->
                g.color = withAlpha(c, cam.chrome)
                val dx = r.left + 16f * scale + i * (dot + 9f * scale)
                g.fill(Ellipse2D.Float(dx, r.top - bar / 2 - dot / 2, dot, dot))
            }
            if (cam.projector > 0.01f) {
                g.font = DemoFonts.sans(400, 15f * scale / 0.8f)
                g.color = withAlpha(MUTED, cam.projector * cam.chrome)
                val lx = r.left + 16f * scale + 3 * (dot + 9f * scale) + 12f * scale
                g.drawString("Screen 1 · Operator", lx, r.top - bar / 2 + 6f * scale)
            }
        }
        g.clip(Rectangle2D.Float(r.left, r.top, r.width, r.height))
        drawScaled(g, s.appBefore ?: s.app, r)
        if (s.appBefore != null) {
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, s.appFade)
            drawScaled(g, s.app, r)
            g.composite = AlphaComposite.SrcOver
        }
        g.clip = old
        g.color = Color(255, 255, 255, 36)
        g.stroke = BasicStroke(1f)
        g.draw(frame)
    }

    private fun projectorPanel(g: Graphics2D, s: Scene, alpha: Float) {
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        // Header: tab name in the accent, what is being shown in grey.
        g.font = DemoFonts.sans(600, 22f, 0.14f)
        g.color = ACCENT
        g.drawString(s.header.first.uppercase(), 56f, 92f)
        val w = g.fontMetrics.stringWidth(s.header.first.uppercase())
        g.font = DemoFonts.sans(400, 22f)
        g.color = FAINT
        g.drawString(s.header.second, 56f + w + 16f, 92f)

        g.font = DemoFonts.sans(600, 24f)
        g.color = INK
        g.drawString("Screen 2 · Projector", SCREEN.left, 326f)
        if (s.live) liveBadge(g, s.liveAge)

        glow(g, SCREEN.center.x, SCREEN.center.y, 330f, 0.10f)
        softShadow(g, SCREEN, Shadow(20f, 12f, 10f, 30, 5))
        val shape = RoundRectangle2D.Float(SCREEN.left, SCREEN.top, SCREEN.width, SCREEN.height, 20f, 20f)
        g.color = Color.BLACK
        g.fill(shape)
        val old = g.clip
        g.clip(shape)
        drawScaled(g, s.projectorBefore ?: s.projector, SCREEN)
        if (s.projectorBefore != null) {
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, s.projectorFade * alpha)
            drawScaled(g, s.projector, SCREEN)
        }
        g.clip = old
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        g.color = Color(255, 255, 255, 46)
        g.stroke = BasicStroke(1f)
        g.draw(shape)

        if (s.arrow > 0f) dashedArrow(g, s.arrow)
        g.composite = AlphaComposite.SrcOver
    }

    private fun liveBadge(g: Graphics2D, age: Int) {
        val pulse = if (age < 15) sin(age / 15f * PI).toFloat() else 0f
        g.font = DemoFonts.sans(700, 18f, 0.1f)
        val w = g.fontMetrics.stringWidth("LIVE") + 24f + 16f
        val x = SCREEN.right - w
        val y = 300f
        if (pulse > 0f) {
            g.color = withAlpha(LIVE_RED, 0.35f * pulse)
            val grow = 14f * pulse
            g.fill(RoundRectangle2D.Float(x - grow / 2, y - grow / 2, w + grow, 32f + grow, 12f + grow, 12f + grow))
        }
        g.color = LIVE_RED
        g.fill(RoundRectangle2D.Float(x, y, w, 32f, 12f, 12f))
        g.color = Color.WHITE
        g.fill(Ellipse2D.Float(x + 12f, y + 12f, 8f, 8f))
        g.drawString("LIVE", x + 28f, y + 23f)
    }

    private fun dashedArrow(g: Graphics2D, t: Float) {
        val y = SCREEN.center.y
        val x0 = OPERATOR_DST.right + 16f
        val x1 = SCREEN.left - 14f
        val end = x0 + (x1 - x0) * ease(t)
        g.color = ACCENT
        g.stroke = BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, floatArrayOf(6f, 8f), 0f)
        g.drawLine(x0.toInt(), y.toInt(), (end - 6f).toInt(), y.toInt())
        if (t >= 0.95f) {
            g.stroke = BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val p = GeneralPath()
            p.moveTo(x1 - 12f, y - 8f)
            p.lineTo(x1, y)
            p.lineTo(x1 - 12f, y + 8f)
            g.draw(p)
        }
    }

    // ── Outline, dim, tooltip ─────────────────────────────────────────────

    private fun focus(g: Graphics2D, s: Scene, f: Focus) {
        val age = s.focusAge
        val draw = ease(min(1f, age / 9f))
        val compact = f.compact
        val pad = if (compact) 4f else 6f
        val r = s.camera.map(f.rect)
        val grow = 1f + 0.06f * (1 - draw)
        val cx = r.center.x
        val cy = r.center.y
        val w = (r.width + pad * 2) * grow
        val h = (r.height + pad * 2) * grow
        val radius = if (compact) 20f else 28f
        val hole = RoundRectangle2D.Float(cx - w / 2, cy - h / 2, w, h, radius, radius)

        val dim = Area(Rectangle2D.Float(0f, 0f, OUT_W.toFloat(), OUT_H.toFloat()))
        dim.subtract(Area(hole))
        g.color = Color(0, 0, 0, (128 * draw).toInt())
        g.fill(dim)

        // Halo: 0 0 0 10px rgba(134,239,172,.18), breathing every 1.6s while held.
        val breathe = 0.5f + 0.5f * sin(age / (1.6f * 30f) * 2 * PI).toFloat()
        val halo = (if (compact) 8f else 10f) + 6f * breathe * (if (age > 12) 1f else 0f)
        g.color = withAlpha(ACCENT, 0.18f * draw)
        g.stroke = BasicStroke(halo * 2)
        g.draw(RoundRectangle2D.Float(hole.x - 0f, hole.y - 0f, hole.width, hole.height, radius + halo, radius + halo))
        g.color = withAlpha(ACCENT, draw)
        g.stroke = BasicStroke(if (compact) 3f else 4f)
        g.draw(hole)

        val tipT = ease(((age - 4.5f) / 6f).coerceIn(0f, 1f))
        if (tipT > 0f) tooltip(g, f, Rect(hole.x, hole.y, hole.x + hole.width, hole.y + hole.height), tipT)
    }

    /** The two tooltip sizes: full for feature steps, compact over the shrunken operator window. */
    private class TipStyle(compact: Boolean, numbered: Boolean) {
        val padX = if (compact) 22f else 28f
        val padY = if (compact) 16f else 22f
        val badge = if (!numbered) 0f else if (compact) 38f else 48f
        val gap = if (!numbered) 0f else if (compact) 14f else 18f
        val title = DemoFonts.sans(600, if (compact) 26f else 34f)
        val badgeFont = DemoFonts.sans(700, if (compact) 20f else 26f)
        val badgeDrop = if (compact) 7f else 9f
        val titleLine = if (compact) 32f else 42f
        val titleAscent = if (compact) 26f else 34f
        val corner = if (compact) 24f else 28f
        val arrow = if (compact) 11f else 14f
        val maxWidth = if (numbered) 640f else 560f
    }

    private fun tooltip(g: Graphics2D, f: Focus, hole: Rect, t: Float) {
        val st = TipStyle(f.compact, f.step != null)
        val bodyFont = DemoFonts.sans(400, 26f)
        val maxText = st.maxWidth - st.padX * 2 - st.badge - st.gap
        val lines = if (f.compact || f.body.isBlank()) emptyList() else wrap(g, f.body, bodyFont, maxText)
        val bodyW = lines.maxOfOrNull { g.getFontMetrics(bodyFont).stringWidth(it).toFloat() } ?: 0f
        val textW = max(g.getFontMetrics(st.title).stringWidth(f.title).toFloat(), bodyW)
        val w = st.padX * 2 + st.badge + st.gap + textW
        val textH = st.titleLine + if (lines.isEmpty()) 0f else 8f + lines.size * 36.4f
        val h = st.padY * 2 + max(st.badge, textH)

        val arrowX = (hole.left + 54f).coerceIn(hole.left + 14f, max(hole.left + 14f, hole.right - 14f))
        val x = (arrowX - 54f).coerceIn(40f, OUT_W - 40f - w)
        val below = hole.bottom + 14f + h + 40f < OUT_H
        val rise = (1 - t) * 8f
        val y = if (below) hole.bottom + 14f + rise else hole.top - 14f - h - rise

        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t)
        tipBox(g, st, Rect(x, y, x + w, y + h), arrowX, below)
        var tx = x + st.padX
        if (f.step != null) {
            val by = if (lines.isEmpty()) y + (h - st.badge) / 2 else y + st.padY
            g.color = ACCENT
            g.fill(Ellipse2D.Float(tx, by, st.badge, st.badge))
            g.color = BG
            g.font = st.badgeFont
            centered(g, f.step.toString(), tx + st.badge / 2, by + st.badge / 2 + st.badgeDrop)
            tx += st.badge + st.gap
        }
        g.font = st.title
        g.color = INK
        val titleBase = if (lines.isEmpty()) y + h / 2 + st.titleAscent * 0.35f else y + st.padY + st.titleAscent
        g.drawString(f.title, tx, titleBase)
        g.font = bodyFont
        g.color = MUTED
        lines.forEachIndexed { i, line -> g.drawString(line, tx, titleBase + 4f + 36.4f * (i + 1)) }
        g.composite = AlphaComposite.SrcOver
    }

    /** The tooltip's panel: shadow, #18181b fill, 2px accent border and the arrow toward its target. */
    private fun tipBox(g: Graphics2D, st: TipStyle, r: Rect, arrowX: Float, below: Boolean) {
        softShadow(g, r, Shadow(14f, 9f, 28f, 34, 5))
        val box = RoundRectangle2D.Float(r.left, r.top, r.width, r.height, st.corner, st.corner)
        g.color = PANEL
        g.fill(box)
        g.color = ACCENT
        g.stroke = BasicStroke(2f)
        g.draw(box)
        val a = st.arrow
        val edge = if (below) r.top + 1f else r.bottom - 1f
        val point = if (below) r.top - a else r.bottom + a
        val tip = GeneralPath()
        tip.moveTo(arrowX - a, edge)
        tip.lineTo(arrowX, point)
        tip.lineTo(arrowX + a, edge)
        tip.closePath()
        g.fill(tip)
    }

    // ── Cursor, keys, caption, progress ───────────────────────────────────

    private fun cursor(g: Graphics2D, s: Scene, at: Pt) {
        s.ripple?.let { t ->
            val r = 40f * ease(t)
            g.color = withAlpha(ACCENT, 1f - t)
            g.stroke = BasicStroke(3f)
            g.draw(Ellipse2D.Float(at.x - r, at.y - r, r * 2, r * 2))
        }
        // The macOS arrow from the storyboard: 22×28 drawn at 2×, tip at (2, 2).
        val k = 2f * if (s.pressed) 0.9f else 1f
        val p = GeneralPath()
        val pts = listOf(2f to 2f, 2f to 22f, 7.5f to 17f, 11f to 25f, 14f to 23.6f, 10.6f to 16f, 18f to 16f)
        pts.forEachIndexed { i, (x, y) ->
            val px = at.x + (x - 2f) * k
            val py = at.y + (y - 2f) * k
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        p.closePath()
        g.color = Color.WHITE
        g.fill(p)
        g.color = Color.BLACK
        g.stroke = BasicStroke(1.6f * k, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(p)
        s.pointerTag?.let { tag ->
            g.font = DemoFonts.sans(600, 22f)
            val w = g.fontMetrics.stringWidth(tag) + 28f
            val pill = RoundRectangle2D.Float(at.x + 34f, at.y + 44f, w, 40f, 40f, 40f)
            g.color = PANEL
            g.fill(pill)
            g.color = ACCENT
            g.stroke = BasicStroke(2f)
            g.draw(pill)
            g.color = INK
            g.drawString(tag, at.x + 48f, at.y + 72f)
        }
    }

    private fun keycap(g: Graphics2D, s: Scene, label: String) {
        val anchor = s.focus?.let { s.camera.map(it.rect) } ?: return
        g.font = DemoFonts.sans(600, 34f)
        val w = max(64f, g.fontMetrics.stringWidth(label) + 40f)
        val x = anchor.right + 28f
        val y = anchor.center.y - 32f
        val cap = RoundRectangle2D.Float(x.coerceAtMost(OUT_W - w - 40f), y, w, 64f, 22f, 22f)
        g.color = PANEL
        g.fill(cap)
        g.color = ACCENT
        g.stroke = BasicStroke(2f)
        g.draw(cap)
        g.color = INK
        centered(g, label, cap.x + w / 2, y + 44f)
    }

    private fun caption(g: Graphics2D, text: String, t: Float) {
        g.font = DemoFonts.sans(400, 32f)
        val w = g.fontMetrics.stringWidth(text) + 64f + 28f
        val x = (OUT_W - w) / 2
        val y = OUT_H - 56f - 76f + (1 - t) * 8f
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, t)
        val pill = RoundRectangle2D.Float(x, y, w, 76f, 28f, 28f)
        g.color = Color(24, 24, 27, 240)
        g.fill(pill)
        g.color = Color(255, 255, 255, 30)
        g.stroke = BasicStroke(1f)
        g.draw(pill)
        g.color = ACCENT
        g.fill(Ellipse2D.Float(x + 32f, y + 33f, 10f, 10f))
        g.color = INK
        g.drawString(text, x + 60f, y + 49f)
        g.composite = AlphaComposite.SrcOver
    }

    private fun progress(g: Graphics2D, step: Int, steps: Int) {
        val gap = 8f
        val small = min(24f, (420f - 48f) / max(1, steps - 1) - gap)
        var x = OUT_W - 56f - (48f + (steps - 1) * (small + gap))
        for (i in 1..steps) {
            val w = if (i == step) 48f else small
            g.color = if (i <= step) ACCENT else Color(255, 255, 255, 46)
            g.fill(RoundRectangle2D.Float(x, 52f, w, 5f, 5f, 5f))
            x += w + gap
        }
    }

    // ── Cards ─────────────────────────────────────────────────────────────

    private fun card(draw: (Graphics2D) -> Unit): BufferedImage {
        val img = BufferedImage(OUT_W, OUT_H, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.quality()
        g.color = BG
        g.fillRect(0, 0, OUT_W, OUT_H)
        draw(g)
        g.dispose()
        return img
    }

    /** "The <accent> Tab": the accent word in Cormorant Garamond italic and the accent color. */
    private fun title(g: Graphics2D, accent: String, size: Float, x: Float, baseline: Float) {
        val before = "The "
        val after = " Tab"
        val sans = DemoFonts.sans(500, size, -0.04f)
        val serif = DemoFonts.serifItalic(size * 1.08f)
        var cx = x
        g.color = INK
        g.font = sans
        g.drawString(before, cx, baseline)
        cx += g.fontMetrics.stringWidth(before)
        g.color = ACCENT
        g.font = serif
        g.drawString(accent, cx, baseline)
        cx += g.fontMetrics.stringWidth(accent) + size * 0.04f
        g.color = INK
        g.font = sans
        g.drawString(after, cx, baseline)
    }

    private fun titleWidth(g: Graphics2D, accent: String, size: Float): Float {
        val before = "The "
        val after = " Tab"
        val sans = g.getFontMetrics(DemoFonts.sans(500, size, -0.04f))
        val serif = g.getFontMetrics(DemoFonts.serifItalic(size * 1.08f))
        return (sans.stringWidth(before) + serif.stringWidth(accent) + sans.stringWidth(after)) + size * 0.04f
    }

    private fun tabChips(g: Graphics2D, current: String, x0: Float, y: Float) {
        g.font = DemoFonts.sans(400, 26f)
        var x = x0
        for (tab in TABS) {
            val selected = tab == current
            g.font = DemoFonts.sans(if (selected) 600 else 400, 26f)
            val w = g.fontMetrics.stringWidth(tab) + 40f
            val chip = RoundRectangle2D.Float(x, y, w, 52f, 20f, 20f)
            if (selected) {
                g.color = ACCENT
                g.fill(chip)
                g.color = BG
            } else {
                g.color = Color(255, 255, 255, 26)
                g.stroke = BasicStroke(1f)
                g.draw(chip)
                g.color = FAINT
            }
            g.drawString(tab, x + 20f, y + 35f)
            x += w + 14f
        }
    }

    private fun glow(g: Graphics2D, cx: Float, cy: Float, r: Float, alpha: Float) {
        val old = g.paint
        g.paint = RadialGradientPaint(
            Point2D.Float(cx, cy), r * 1.4f, floatArrayOf(0f, 0.7f, 1f),
            arrayOf(withAlpha(ACCENT, alpha), withAlpha(ACCENT, alpha * 0.25f), withAlpha(ACCENT, 0f)),
            MultipleGradientPaint.CycleMethod.NO_CYCLE,
        )
        g.fill(Ellipse2D.Float(cx - r * 1.4f, cy - r * 1.4f, r * 2.8f, r * 2.8f))
        g.paint = old
    }

    /** A drop shadow: [layers] translucent layers, each [step] wider, [drop] below its box. */
    private class Shadow(val drop: Float, val step: Float, val radius: Float, val alpha: Int, val layers: Int)

    /** Draws [shadow] under [r]; layered rather than blurred, which Java2D cannot do cheaply. */
    private fun softShadow(g: Graphics2D, r: Rect, shadow: Shadow) {
        g.color = Color(0, 0, 0, shadow.alpha)
        for (i in 1..shadow.layers) {
            val grow = i * shadow.step
            val corner = shadow.radius + grow
            val top = r.top + shadow.drop
            g.fill(RoundRectangle2D.Float(r.left - grow / 2, top, r.width + grow, r.height + grow, corner, corner))
        }
    }

    private fun drawScaled(g: Graphics2D, img: BufferedImage, to: Rect) {
        g.drawImage(img, to.left.toInt(), to.top.toInt(), to.width.toInt(), to.height.toInt(), null)
    }

    private fun wrap(g: Graphics2D, text: String, font: Font, width: Float): List<String> {
        val fm = g.getFontMetrics(font)
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (fm.stringWidth(next) > width && line.isNotEmpty()) {
                lines += line
                line = word
            } else {
                line = next
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private fun centered(g: Graphics2D, text: String, cx: Float, baseline: Float) {
        g.drawString(text, cx - g.fontMetrics.stringWidth(text) / 2f, baseline)
    }

    private fun Graphics2D.quality() {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }
}

/**
 * The storyboard's two faces: Inter standing in for the system sans, Cormorant Garamond italic for accents.
 * They live in the git-ignored `demo-fonts/`; without them, Helvetica Neue and Georgia italic stand in.
 */
internal object DemoFonts {
    private val faces = mutableMapOf<String, Font>()

    private fun face(file: String) = faces.getOrPut(file) {
        DemoFonts::class.java.getResourceAsStream("/demo-fonts/$file")?.use { Font.createFont(Font.TRUETYPE_FONT, it) }
            ?: when {
                file.startsWith("Cormorant") -> Font("Georgia", Font.ITALIC, 1)
                file.contains("Bold") || file.contains("SemiBold") -> Font("Helvetica Neue", Font.BOLD, 1)
                else -> Font("Helvetica Neue", Font.PLAIN, 1)
            }
    }

    fun sans(weight: Int, size: Float, tracking: Float = 0f): Font {
        val file = when {
            weight >= 700 -> "Inter-Bold.ttf"
            weight >= 600 -> "Inter-SemiBold.ttf"
            weight >= 500 -> "Inter-Medium.ttf"
            else -> "Inter-Regular.ttf"
        }
        val font = face(file).deriveFont(size)
        return if (tracking == 0f) font else font.deriveFont(mapOf(TextAttribute.TRACKING to tracking))
    }

    fun serifItalic(size: Float): Font = face("CormorantGaramond-Italic.ttf").deriveFont(size)
}

internal fun ease(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).let { it * it * it } / 2
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun lerp(a: Rect, b: Rect, t: Float) =
    Rect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

private fun withAlpha(c: Color, a: Float) = Color(c.red, c.green, c.blue, (255 * a.coerceIn(0f, 1f)).toInt())
