package presentation.engine

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.xmlbeans.XmlObject
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/** Programmatic test fixtures — no binary files committed for these formats. */
object Fixtures {

    /** A .pptx with one slide per (bodyText, notesText) pair. */
    fun createPptx(dir: File, slides: List<Pair<String, String>>, name: String = "fixture.pptx"): File {
        val file = File(dir, name)
        XMLSlideShow().use { ppt ->
            for ((body, noteText) in slides) {
                val slide = ppt.createSlide()
                val box = slide.createTextBox()
                box.anchor = Rectangle(50, 50, 500, 120)
                box.text = body
                val shape = slide.createAutoShape()
                shape.anchor = Rectangle(80, 220, 240, 120)
                shape.fillColor = Color(0x33, 0x66, 0x99)
                if (noteText.isNotBlank()) {
                    val notes = ppt.getNotesSlide(slide)
                    val placeholder = notes.placeholders.filterIsInstance<XSLFTextShape>()
                        .firstOrNull { it.textType?.name?.contains("BODY") == true }
                        ?: notes.placeholders.getOrNull(1)
                    placeholder?.text = noteText
                }
            }
            file.outputStream().use { ppt.write(it) }
        }
        return file
    }

    /** A PDF with [pages] pages, each labeled "Page N". */
    fun createPdf(dir: File, pages: Int, name: String = "fixture.pdf"): File {
        val file = File(dir, name)
        PDDocument().use { doc ->
            repeat(pages) { i ->
                val page = PDPage(PDRectangle(720f, 405f))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font.HELVETICA_BOLD, 36f)
                    content.newLineAtOffset(60f, 300f)
                    content.showText("Page ${i + 1}")
                    content.endText()
                    content.setNonStrokingColor(Color(200, 40, 40))
                    content.addRect(60f, 60f, 200f, 100f)
                    content.fill()
                }
            }
            doc.save(file)
        }
        return file
    }

    /** An animation target for [addTiming]. */
    data class TimingTarget(
        val shapeId: Long,
        /** Paragraph indices when the shape builds by paragraph; empty = whole-shape target. */
        val paragraphs: List<Int> = emptyList()
    )

    /**
     * Injects a realistic `<p:timing>` main sequence into [slide]: one click step per target,
     * each with a `set style.visibility` + `animEffect fade` behavior — the same shape
     * PowerPoint itself writes for a Fade entrance.
     */
    fun addTiming(slide: XSLFSlide, targets: List<TimingTarget>) {
        val pNs = "http://schemas.openxmlformats.org/presentationml/2006/main"
        val aNs = "http://schemas.openxmlformats.org/drawingml/2006/main"
        var nextId = 10
        val steps = StringBuilder()
        for (target in targets) {
            val tgtElements = if (target.paragraphs.isEmpty()) {
                listOf("""<p:spTgt spid="${target.shapeId}"/>""")
            } else {
                target.paragraphs.map { p ->
                    """<p:spTgt spid="${target.shapeId}"><p:txEl><p:pRg st="$p" end="$p"/></p:txEl></p:spTgt>"""
                }
            }
            for (tgt in tgtElements) {
                val clickId = nextId++
                val innerId = nextId++
                val effectId = nextId++
                val setId = nextId++
                val fadeId = nextId++
                steps.append(
                    """
                    <p:par><p:cTn id="$clickId" fill="hold">
                      <p:stCondLst><p:cond delay="indefinite"/></p:stCondLst>
                      <p:childTnLst><p:par><p:cTn id="$innerId" fill="hold">
                        <p:stCondLst><p:cond delay="0"/></p:stCondLst>
                        <p:childTnLst><p:par>
                          <p:cTn id="$effectId" presetID="10" presetClass="entr" presetSubtype="0" fill="hold" grpId="0" nodeType="clickEffect">
                            <p:stCondLst><p:cond delay="0"/></p:stCondLst>
                            <p:childTnLst>
                              <p:set>
                                <p:cBhvr>
                                  <p:cTn id="$setId" dur="1" fill="hold"><p:stCondLst><p:cond delay="0"/></p:stCondLst></p:cTn>
                                  <p:tgtEl>$tgt</p:tgtEl>
                                  <p:attrNameLst><p:attrName>style.visibility</p:attrName></p:attrNameLst>
                                </p:cBhvr>
                                <p:to><p:strVal val="visible"/></p:to>
                              </p:set>
                              <p:animEffect transition="in" filter="fade">
                                <p:cBhvr>
                                  <p:cTn id="$fadeId" dur="500"/>
                                  <p:tgtEl>$tgt</p:tgtEl>
                                </p:cBhvr>
                              </p:animEffect>
                            </p:childTnLst>
                          </p:cTn>
                        </p:par></p:childTnLst>
                      </p:cTn></p:par></p:childTnLst>
                    </p:cTn></p:par>
                    """.trimIndent()
                )
            }
        }
        val timingXml = """
            <p:timing xmlns:p="$pNs" xmlns:a="$aNs">
              <p:tnLst><p:par>
                <p:cTn id="1" dur="indefinite" restart="never" nodeType="tmRoot">
                  <p:childTnLst><p:seq concurrent="1" nextAc="seek">
                    <p:cTn id="2" dur="indefinite" nodeType="mainSeq">
                      <p:childTnLst>$steps</p:childTnLst>
                    </p:cTn>
                  </p:seq></p:childTnLst>
                </p:cTn>
              </p:par></p:tnLst>
            </p:timing>
        """.trimIndent()

        val fragment = XmlObject.Factory.parse(timingXml)
        val source = fragment.newCursor()
        source.toFirstChild()
        val target = slide.xmlObject.newCursor()
        target.toEndToken()
        source.copyXml(target)
        source.dispose()
        target.dispose()
    }

    fun jpegBytes(width: Int, height: Int, color: Color): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", baos)
        return baos.toByteArray()
    }

    /**
     * A synthetic .key zip. [previewPdf] lands at QuickLook/Preview.pdf when non-null;
     * [thumbnails] maps `st-` file names (placed under Data/) to JPEG bytes; [slideIwaIds]
     * produce empty Index/Slide-<id>.iwa entries in the given (presentation) order.
     */
    fun createKeynoteZip(
        dir: File,
        previewPdf: ByteArray?,
        thumbnails: Map<String, ByteArray>,
        slideIwaIds: List<Long>,
        name: String = "fixture.key"
    ): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            if (previewPdf != null) {
                zip.putNextEntry(ZipEntry("QuickLook/Preview.pdf"))
                zip.write(previewPdf)
                zip.closeEntry()
            }
            for (id in slideIwaIds) {
                zip.putNextEntry(ZipEntry("Index/Slide-$id.iwa"))
                zip.write(ByteArray(4))
                zip.closeEntry()
            }
            for ((thumbName, bytes) in thumbnails) {
                zip.putNextEntry(ZipEntry("Data/$thumbName"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }
}
