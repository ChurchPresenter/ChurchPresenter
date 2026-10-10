package org.churchpresenter.presentationengine

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.churchpresenter.presentationengine.Fixtures.ProtoWriter
import org.churchpresenter.presentationengine.keynote.KnFields as F
import org.churchpresenter.presentationengine.tools.DumpKeynote
import org.churchpresenter.presentationengine.tools.DumpTiming
import org.churchpresenter.presentationengine.tools.MakeSampleDeck
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DumpToolsTest {

    private val temp: File = Files.createTempDirectory("dump-tools-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private class Captured(val out: String, val err: String)

    private fun capture(run: (PrintStream, PrintStream) -> Unit): Captured {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        PrintStream(out, true, Charsets.UTF_8).use { o ->
            PrintStream(err, true, Charsets.UTF_8).use { e -> run(o, e) }
        }
        return Captured(out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8))
    }

    private fun timing(vararg args: String) = capture { o, e -> DumpTiming.dump(arrayOf(*args), o, e) }

    private fun keynote(vararg args: String) = capture { o, e -> DumpKeynote.dump(arrayOf(*args), o, e) }

    private fun sampleDeck(): File {
        val file = File(temp, "sample.pptx")
        MakeSampleDeck.main(arrayOf(file.absolutePath))
        return file
    }

    @Test
    fun `the sample deck is written with builds and transitions`() {
        val deck = (PresentationLoader.load(sampleDeck()) as LoadResult.Success).deck
        assertTrue(deck.slideCount >= 3)
        assertTrue(deck.slides.first().timeline != null, "slide one builds")
        assertTrue(deck.slides.drop(1).any { it.transition != null }, "a later slide has a transition")
    }

    @Test
    fun `dump timing with no arguments prints its usage`() {
        val result = timing()
        assertTrue(result.err.startsWith("usage: DumpTiming"))
        assertEquals("", result.out)
    }

    @Test
    fun `dump timing reports a deck it cannot load`() {
        val result = timing(File(temp, "missing.pptx").absolutePath)
        assertTrue(result.out.startsWith("LOAD FAILED:"), result.out)
    }

    @Test
    fun `dump timing lists layers, steps and transitions and smoke-renders the first slide`() {
        val out = timing(sampleDeck().absolutePath).out
        assertTrue(out.contains("=== sample.pptx — PPTX"), out)
        assertTrue(out.contains("shapes=["), "a background band")
        assertTrue(out.contains("shapeIndex="), "a shape layer")
        assertTrue(out.contains("para=1"), "a paragraph layer")
        assertTrue(out.contains("step 1:"))
        assertTrue(out.contains("transition=PUSH"))
        assertTrue(out.contains("First slide renders at 960x"), out)
    }

    @Test
    fun `dump timing writes every slide as a png when given a directory`() {
        val outDir = File(temp, "frames")
        val pdf = Fixtures.createPdf(temp, pages = 2, name = "frames.pdf")
        val out = timing(pdf.absolutePath, outDir.absolutePath).out
        val pngs = outDir.listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf("slide_01.png", "slide_02.png"), pngs)
        assertEquals("slide_01.png", pngs.first())
        assertTrue(out.contains("Wrote ${File(outDir, "slide_01.png").absolutePath}"))
    }

    @Test
    fun `dump timing names a static deck and its warnings`() {
        val pdf = timing(Fixtures.createPdf(temp, pages = 1).absolutePath).out
        assertTrue(pdf.contains("static"))
        assertTrue(pdf.contains("(no timeline)"))

        val pptx = Fixtures.createPptx(temp, listOf("Moving" to ""), name = "motion.pptx")
        val rewritten = File(temp, "motion-rewritten.pptx")
        XMLSlideShow(pptx.inputStream()).use { show ->
            val slide = show.slides.first()
            val spid = slide.shapes.first().shapeId
            Fixtures.addRawTiming(
                slide,
                """
                <p:par><p:cTn id="10" fill="hold"><p:stCondLst><p:cond delay="indefinite"/></p:stCondLst>
                  <p:childTnLst><p:par><p:cTn id="11" fill="hold"><p:stCondLst><p:cond delay="0"/></p:stCondLst>
                    <p:childTnLst><p:par><p:cTn id="12" presetID="0" presetClass="path" nodeType="clickEffect">
                      <p:stCondLst><p:cond delay="0"/></p:stCondLst>
                      <p:childTnLst>
                        <p:animMotion origin="layout" path="M 0 0 L 0.25 0 E" pathEditMode="relative">
                          <p:cBhvr><p:cTn id="13" dur="500"/><p:tgtEl><p:spTgt spid="$spid"/></p:tgtEl></p:cBhvr>
                        </p:animMotion>
                        <p:animEffect transition="in" filter="nonsense">
                          <p:cBhvr><p:cTn id="14" dur="500"/><p:tgtEl><p:spTgt spid="999"/></p:tgtEl></p:cBhvr>
                        </p:animEffect>
                      </p:childTnLst>
                    </p:cTn></p:par></p:childTnLst>
                  </p:cTn></p:par></p:childTnLst>
                </p:cTn></p:par>
                """.trimIndent(),
            )
            rewritten.outputStream().use { show.write(it) }
        }
        val motion = timing(rewritten.absolutePath).out
        assertTrue(motion.contains("Custom(EMPHASIS, curves="), motion)
        assertTrue(motion.contains("DEGRADE WARNINGS ("), motion)
    }

    @Test
    fun `dump timing says when a deck degraded nothing`() {
        val out = timing(Fixtures.createPdf(temp, pages = 1, name = "clean.pdf").absolutePath).out
        assertTrue(out.contains("No degrade warnings"), out)
    }

    // ── DumpKeynote ───────────────────────────────────────────────────────────

    private fun proto(block: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(block).toByteArray()

    private fun reference(id: Long) = proto { varintField(F.REFERENCE_IDENTIFIER, id) }

    private fun geometry() = proto {
        bytesField(F.GEOMETRY_POSITION, proto { floatField(F.POINT_X, 10f); floatField(F.POINT_Y, 20f) })
        bytesField(F.GEOMETRY_SIZE, proto { floatField(F.SIZE_WIDTH, 100f); floatField(F.SIZE_HEIGHT, 50f) })
    }

    private fun drawableSuper() = proto { bytesField(F.DRAWABLE_GEOMETRY, geometry()) }

    private fun keynoteDeck(): File {
        val show = proto {
            bytesField(F.SHOW_SLIDE_TREE, proto { bytesField(F.SLIDE_TREE_SLIDES, reference(100L)) })
            bytesField(F.SHOW_SIZE, proto { floatField(F.SIZE_WIDTH, 1920f); floatField(F.SIZE_HEIGHT, 1080f) })
        }
        val parentNode = proto {
            bytesField(F.SLIDE_NODE_CHILDREN, reference(101L))
            bytesField(F.SLIDE_NODE_SLIDE, reference(200L))
        }
        val childNode = proto {
            bytesField(F.SLIDE_NODE_SLIDE, reference(201L))
            varintField(F.SLIDE_NODE_IS_SKIPPED, 1)
            bytesField(F.SLIDE_NODE_CHILDREN, reference(999L))
        }
        val transition = proto {
            bytesField(F.TRANSITION_ATTRIBUTES, proto {
                bytesField(F.TRANSITION_ATTRS_ANIMATION, proto {
                    stringField(F.ANIM_ATTRS_TYPE, "In")
                    stringField(F.ANIM_ATTRS_EFFECT, "apple:transition:Dissolve")
                    doubleField(F.ANIM_ATTRS_DURATION, 1.0)
                })
            })
        }
        val slide = proto {
            bytesField(5, reference(250L))
            listOf(300L, 310L, 320L, 330L).forEach { bytesField(F.SLIDE_DRAWABLES_Z_ORDER, reference(it)) }
            bytesField(F.SLIDE_BUILDS, reference(500L))
            bytesField(F.SLIDE_BUILD_CHUNKS, reference(510L))
            bytesField(F.SLIDE_TRANSITION, transition)
        }
        val ownedOnlySlide = proto { bytesField(F.SLIDE_OWNED_DRAWABLES, reference(310L)) }
        val textShape = proto {
            bytesField(F.SHAPE_INFO_SUPER, proto { bytesField(F.SHAPE_SUPER, drawableSuper()) })
            bytesField(F.SHAPE_INFO_OWNED_STORAGE, reference(400L))
        }
        val placeholder = proto { bytesField(F.PLACEHOLDER_SUPER, textShape) }
        val group = proto {
            bytesField(F.GROUP_SUPER, drawableSuper())
            bytesField(F.GROUP_CHILDREN, reference(330L))
        }
        val image = proto { bytesField(F.IMAGE_SUPER, drawableSuper()) }
        val movie = proto {
            bytesField(F.MOVIE_SUPER, drawableSuper())
            bytesField(F.MOVIE_DATA, proto { varintField(F.DATA_REFERENCE_IDENTIFIER, 7) })
            varintField(20, 1)
        }
        val build = proto {
            bytesField(F.BUILD_DRAWABLE, reference(300L))
            stringField(F.BUILD_DELIVERY, "By Paragraph")
            bytesField(F.BUILD_ATTRIBUTES, proto {
                val anim = proto { stringField(F.ANIM_ATTRS_EFFECT, "apple:build-effect:Dissolve") }
                bytesField(F.BUILD_ATTRS_ANIMATION, anim)
            })
        }
        val chunk = proto {
            bytesField(F.BUILD_CHUNK_BUILD, reference(500L))
            varintField(F.BUILD_CHUNK_AUTOMATIC, 1)
            doubleField(F.BUILD_CHUNK_DELAY, 0.25)
        }
        val objects = listOf(
            Triple(1L, F.TYPE_KN_DOCUMENT, proto { bytesField(F.DOCUMENT_SHOW, reference(2L)) }),
            Triple(2L, F.TYPE_KN_SHOW, show),
            Triple(100L, F.TYPE_KN_SLIDE_NODE, parentNode),
            Triple(101L, F.TYPE_KN_SLIDE_NODE, childNode),
            Triple(200L, F.TYPE_KN_SLIDE, slide),
            Triple(201L, F.TYPE_KN_SLIDE, ownedOnlySlide),
            Triple(250L, F.TYPE_KN_PLACEHOLDER, placeholder),
            Triple(300L, F.TYPE_TSWP_SHAPE_INFO, textShape),
            Triple(310L, F.TYPE_TSD_GROUP, group),
            Triple(320L, F.TYPE_TSD_MOVIE, movie),
            Triple(330L, F.TYPE_TSD_IMAGE, image),
            Triple(400L, F.TYPE_TSWP_STORAGE, proto { stringField(F.STORAGE_TEXT, "Grace and peace") }),
            Triple(500L, F.TYPE_KN_BUILD, build),
            Triple(510L, F.TYPE_KN_BUILD_CHUNK, chunk),
        )
        return Fixtures.writeKeynoteDir(Files.createTempDirectory(temp.toPath(), "key").toFile(), objects)
    }

    @Test
    fun `dump keynote with no arguments prints its usage`() {
        assertTrue(keynote().err.startsWith("usage: DumpKeynote"))
    }

    @Test
    fun `dump keynote reports a file with no objects`() {
        val notKeynote = File(temp, "empty.key").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals("FAILED: no IWA objects parsed", keynote(notKeynote.absolutePath).out.trim())
    }

    @Test
    fun `dump keynote walks the show down to each drawable, build and transition`() {
        val out = keynote(keynoteDeck().absolutePath).out
        assertTrue(out.contains("Objects by type (top 25):"))
        assertTrue(out.contains("Document id=1 → show=2 (type 2)"))
        assertTrue(out.contains("Show size: 1920.0 x 1080.0"))
        assertTrue(out.contains("Slide tree: 1 top-level nodes"))
        assertTrue(out.contains("node 101") && out.contains("skipped=true"), "the child node, one level down")
        assertTrue(out.contains("placeholder title=250"), out)
        assertTrue(out.contains("text=[Grace and peace]"))
        assertTrue(out.contains("group 310 pos=(10.0,20.0) size=(100.0,50.0)"), out)
        assertTrue(out.contains("child 330:${F.TYPE_TSD_IMAGE}"))
        assertTrue(out.contains("drawable 320:${F.TYPE_TSD_MOVIE}"))
        assertTrue(out.contains("movie.${F.MOVIE_DATA} fields="))
        assertTrue(out.contains("movie.20 scalar varint=1"))
        assertTrue(out.contains("nonAscii=[U+2028]"), out)
        assertTrue(out.contains("build 500 drawable=300 delivery=By Paragraph"))
        assertTrue(out.contains("chunk 510 build=500 auto=true delay=0.25"))
        assertTrue(out.contains("transition: type=In effect=apple:transition:Dissolve dur=1.0"))
        assertTrue(out.contains("owned(f7): 310:${F.TYPE_TSD_GROUP}"), "the second slide has no z-order")
    }

    @Test
    fun `dump keynote stops at a missing document or an unreadable show`() {
        val noDocument = Fixtures.writeKeynoteDir(
            Files.createTempDirectory(temp.toPath(), "nodoc").toFile(),
            listOf(Triple(5L, F.TYPE_KN_SLIDE, proto { varintField(9, 1) })),
        )
        assertTrue(keynote(noDocument.absolutePath).out.contains("No KN.DocumentArchive (type 1) found"))

        val noShow = Fixtures.writeKeynoteDir(
            Files.createTempDirectory(temp.toPath(), "noshow").toFile(),
            listOf(Triple(1L, F.TYPE_KN_DOCUMENT, proto { bytesField(F.DOCUMENT_SHOW, reference(2L)) })),
        )
        val out = keynote(noShow.absolutePath).out
        assertTrue(out.contains("show=2 (type null)"))
        assertTrue(out.contains("Show unreadable"))
    }

    @Test
    fun `dump keynote prints what it can of a sparse graph`() {
        val show = proto { bytesField(F.SHOW_SLIDE_TREE, proto { bytesField(F.SLIDE_TREE_SLIDES, reference(100L)) }) }
        val node = proto {
            bytesField(F.SLIDE_NODE_SLIDE, reference(200L))
            bytesField(F.SLIDE_NODE_CHILDREN, reference(101L))
            bytesField(F.SLIDE_NODE_CHILDREN, reference(102L))
        }
        val slide = proto {
            bytesField(5, reference(250L))
            bytesField(6, reference(251L))
            listOf(300L, 310L, 320L, 330L, 340L).forEach { bytesField(F.SLIDE_OWNED_DRAWABLES, reference(it)) }
            bytesField(F.SLIDE_BUILDS, reference(500L))
            bytesField(F.SLIDE_BUILDS, reference(501L))
            bytesField(F.SLIDE_BUILD_CHUNKS, reference(510L))
            bytesField(F.SLIDE_TRANSITION, proto { varintField(9, 1) })
        }
        val empty = proto { varintField(9, 1) }
        val objects = listOf(
            Triple(1L, F.TYPE_KN_DOCUMENT, proto { bytesField(F.DOCUMENT_SHOW, reference(2L)) }),
            Triple(2L, F.TYPE_KN_SHOW, show),
            Triple(100L, F.TYPE_KN_SLIDE_NODE, node),
            Triple(101L, F.TYPE_KN_SLIDE_NODE, proto { bytesField(F.SLIDE_NODE_SLIDE, reference(999L)) }),
            Triple(200L, F.TYPE_KN_SLIDE, slide),
            Triple(251L, F.TYPE_KN_PLACEHOLDER, empty),
            Triple(300L, F.TYPE_TSWP_SHAPE_INFO, proto { bytesField(F.SHAPE_INFO_OWNED_STORAGE, reference(400L)) }),
            Triple(310L, F.TYPE_TSD_GROUP, empty),
            Triple(320L, F.TYPE_TSD_MOVIE, empty),
            Triple(330L, F.TYPE_TSD_IMAGE, empty),
            Triple(400L, F.TYPE_TSWP_STORAGE, proto { stringField(F.STORAGE_TEXT, "Plain") }),
            Triple(500L, F.TYPE_KN_BUILD, empty),
        )
        val key = Fixtures.writeKeynoteDir(Files.createTempDirectory(temp.toPath(), "sparse").toFile(), objects)
        val out = keynote(key.absolutePath).out
        assertTrue(out.contains("Show size: null x null"))
        assertTrue(out.contains("slide=999 (type null)"))
        assertTrue(out.contains("placeholder title=250 type=null phFields=null storage=null text=null"), out)
        assertTrue(out.contains("placeholder body=251"))
        assertTrue(out.contains("pos=(null,null) size=(null,null) chunks=1 nonAscii=[] text=Plain"), out)
        assertTrue(out.contains("group 310"))
        assertTrue(out.contains("drawable 340:null ?"))
        assertTrue(out.contains("build 500 drawable=null delivery=null type=null"))
        assertTrue(!out.contains("chunk 510"), "a chunk that is not in the index is skipped")
        assertTrue(!out.contains("transition:"))
    }

    @Test
    fun `dump keynote handles a show with no slide tree`() {
        val key = Fixtures.writeKeynoteDir(
            Files.createTempDirectory(temp.toPath(), "notree").toFile(),
            listOf(
                Triple(1L, F.TYPE_KN_DOCUMENT, proto { bytesField(F.DOCUMENT_SHOW, reference(2L)) }),
                Triple(2L, F.TYPE_KN_SHOW, proto { varintField(9, 1) }),
            ),
        )
        val out = keynote(key.absolutePath).out
        assertTrue(out.contains("SlideTree fields: null"))
        assertTrue(out.contains("Slide tree: 0 top-level nodes"))
    }
}
