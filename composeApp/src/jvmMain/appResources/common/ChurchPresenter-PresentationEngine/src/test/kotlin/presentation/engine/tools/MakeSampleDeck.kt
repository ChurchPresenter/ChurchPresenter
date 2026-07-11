package presentation.engine.tools

import org.apache.poi.xslf.usermodel.XMLSlideShow
import presentation.engine.Fixtures
import java.awt.Color
import java.awt.Rectangle
import java.io.File

/**
 * Writes a sample animated .pptx for hands-on testing: builds (whole-shape + by-paragraph),
 * multiple effect kinds, and slide transitions. Usage:
 * `./gradlew makeSampleDeck -Pout=/path/sample.pptx`
 */
object MakeSampleDeck {

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args.firstOrNull() ?: "sample-animated.pptx")
        XMLSlideShow().use { ppt ->
            // Slide 1: title (static) + green rect (fade build) + 3-paragraph list (by paragraph).
            val slide1 = ppt.createSlide()
            val title = slide1.createTextBox()
            title.anchor = Rectangle(40, 20, 640, 60)
            title.text = "Animated deck — slide 1"
            val rect = slide1.createAutoShape()
            rect.anchor = Rectangle(480, 120, 180, 120)
            rect.fillColor = Color(0x2E, 0x7D, 0x32)
            val list = slide1.createTextBox()
            list.anchor = Rectangle(40, 120, 400, 220)
            list.text = "First point appears"
            list.addNewTextParagraph().addNewTextRun().setText("Second point appears")
            list.addNewTextParagraph().addNewTextRun().setText("Third point appears")
            Fixtures.addTiming(
                slide1,
                listOf(
                    Fixtures.TimingTarget(rect.shapeId.toLong()),
                    Fixtures.TimingTarget(list.shapeId.toLong(), paragraphs = listOf(0, 1, 2))
                )
            )

            // Slide 2: static content, push transition from slide 1.
            val slide2 = ppt.createSlide()
            val body2 = slide2.createTextBox()
            body2.anchor = Rectangle(40, 40, 640, 80)
            body2.text = "Slide 2 — arrives with a push transition"
            val rect2 = slide2.createAutoShape()
            rect2.anchor = Rectangle(200, 180, 300, 140)
            rect2.fillColor = Color(0x15, 0x65, 0xC0)
            slide2.xmlObject.addNewTransition().apply {
                addNewPush().dir = org.openxmlformats.schemas.presentationml.x2006.main.STTransitionSideDirectionType.L
            }

            // Slide 3: fade transition + one build.
            val slide3 = ppt.createSlide()
            val body3 = slide3.createTextBox()
            body3.anchor = Rectangle(40, 40, 640, 80)
            body3.text = "Slide 3 — fade transition, one build below"
            val rect3 = slide3.createAutoShape()
            rect3.anchor = Rectangle(240, 200, 240, 120)
            rect3.fillColor = Color(0xC6, 0x28, 0x28)
            slide3.xmlObject.addNewTransition().addNewFade()
            Fixtures.addTiming(slide3, listOf(Fixtures.TimingTarget(rect3.shapeId.toLong())))

            out.outputStream().use { ppt.write(it) }
        }
        println("Wrote ${out.absolutePath}")
    }
}
