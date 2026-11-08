package com.textura.app.model

/** Opaque ARGB colour from 0xRRGGBB. */
fun c(rgb: Int): Int = rgb or -0x1000000

/** Colour with explicit alpha (0..255) from 0xRRGGBB. */
fun ca(alpha: Int, rgb: Int): Int = (alpha shl 24) or (rgb and 0xFFFFFF)

enum class CanvasShape(
    val label: String,
    val wr: Int,
    val hr: Int,
    val forceJustify: Boolean = false,
    val marginScale: Float = 1f,
) {
    Portrait("Portrait", 3, 4),
    Landscape("Landscape", 4, 3),
    Square("Square", 1, 1),
    Story("Story", 9, 16),
    Block("Block", 6, 5, forceJustify = true, marginScale = 0.55f);

    val ratio: Float get() = wr.toFloat() / hr.toFloat()
}

enum class Category(val label: String) {
    Paper("Paper"),
    Typed("Typed"),
    Handwritten("Handwritten"),
    Art("Art"),
    ThreeD("3D"),
}

enum class BgKind { Solid, Gradient, Image }
enum class GradientType { Linear, Radial, Sweep }
enum class Depth(val label: String) { Flat("Flat"), Emboss("Emboss"), Engrave("Engrave"), Extrude("3D") }
enum class Align { Left, Center, Right, Justify }

enum class PaperType(val label: String, val leftPad: Float = 0f, val pad: Float = 0f) {
    None("None"),
    Lined("Lined", leftPad = 0.075f),
    Graph("Graph"),
    Dotted("Dotted"),
    Parchment("Parchment"),
    Letter("Old letter"),
    Sticky("Sticky note"),
    Receipt("Receipt", pad = 0.02f),
    Chalkboard("Chalkboard", pad = 0.035f),
    Newsprint("Newsprint"),
    Kraft("Kraft"),
}

data class Background(
    val kind: BgKind = BgKind.Solid,
    val colors: List<Int> = listOf(c(0x14141A)),
    val gradientType: GradientType = GradientType.Linear,
    val angle: Float = 90f,
    val imagePath: String? = null,
    val blur: Float = 0f,
    val dim: Float = 0f,
    val overlay: List<Int> = emptyList(),
    val overlayAlpha: Float = 0f,
)

/** One complete visual style. Every field has a default so saved JSON stays forward compatible. */
data class Look(
    val id: String = "",
    val name: String = "",
    val category: Category = Category.Art,
    val fontId: String = "montserrat",

    // text
    val textColor: Int = c(0xFFFFFF),
    val textColor2: Int? = null,
    val bold: Boolean = false,
    val uppercase: Boolean = false,
    val align: Align = Align.Left,
    val margin: Float = 0.07f,
    val lineSpacing: Float = 1.0f,
    val letterSpacing: Float = 0f,
    val fillHeight: Boolean = true,

    // typed / handwritten realism
    val handwritten: Boolean = false,
    val imperfection: Float = 0f,

    // depth
    val depth: Depth = Depth.Flat,
    val depthLength: Float = 0.5f,
    val depthAngle: Float = 45f,
    val sideColor: Int = c(0x3A3A48),
    val gradientSide: Boolean = true,
    val highlight: Boolean = true,

    // flat extras
    val outline: Boolean = false,
    val outlineColor: Int = c(0x000000),
    val outlineWidth: Float = 0.05f,
    val shadow: Boolean = false,
    val shadowColor: Int = ca(150, 0x000000),
    val shadowBlur: Float = 0.06f,
    val shadowOffset: Float = 0.05f,

    // paper
    val paper: PaperType = PaperType.None,
    val paperColor: Int = c(0xF6F1E7),
    val paperInset: Float = 0f,
    val tilt: Float = 0f,
    val grain: Float = 0.5f,

    val bg: Background = Background(),
)
