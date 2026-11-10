package com.textura.app.model

/** Opaque ARGB colour from 0xRRGGBB. */
fun c(rgb: Int): Int = rgb or -0x1000000

/** Colour with explicit alpha (0..255) from 0xRRGGBB. */
fun ca(alpha: Int, rgb: Int): Int = (alpha shl 24) or (rgb and 0xFFFFFF)

/** The phone's own screen size in pixels (portrait), filled in when the app starts. */
object DeviceScreen {
    var w: Int = 1080
    var h: Int = 2400
}

enum class CanvasShape(
    val label: String,
    private val wRatio: Int,
    private val hRatio: Int,
    val forceJustify: Boolean = false,
    val marginScale: Float = 1f,
) {
    Portrait("Portrait", 3, 4),
    Landscape("Landscape", 4, 3),
    Square("Square", 1, 1),
    Story("Story 9:16", 9, 16),
    Block("Block", 6, 5, forceJustify = true, marginScale = 0.55f),
    Insta("Insta post 4:5", 4, 5),
    Wide("Wide 16:9", 16, 9),
    Pin("Pinterest 2:3", 2, 3),
    A4("A4 page", 210, 297),
    Cover("Cover banner", 205, 78),
    Screen("Phone screen", 0, 0),
    ScreenWide("Phone landscape", 0, 0);

    val wr: Int get() = when (this) { Screen -> DeviceScreen.w; ScreenWide -> DeviceScreen.h; else -> wRatio }
    val hr: Int get() = when (this) { Screen -> DeviceScreen.h; ScreenWide -> DeviceScreen.w; else -> hRatio }

    val ratio: Float get() = wr.toFloat() / hr.toFloat()
}

/** The outline the text itself is poured into (inside the canvas). */
enum class TextShape(val label: String) {
    Rect("Rectangle"),
    Circle("Circle"),
    Oval("Oval"),
    Heart("Heart"),
    Star("Star"),
    Diamond("Diamond"),
    Hexagon("Hexagon"),
    Bubble("Bubble"),
    Arch("Arch"),
    Triangle("Triangle"),
    Octagon("Octagon"),
    Cross("Cross"),
    Shield("Shield"),
    Drop("Drop"),
    Cloud("Cloud"),
}

enum class Category(val label: String) {
    Paper("Paper"),
    Typed("Typed"),
    Handwritten("Handwritten"),
    Art("Art"),
    ThreeD("3D"),
    Shapes("Shapes"),
    Occasion("Occasion"),
}

enum class BgKind { Solid, Gradient, Image }
enum class GradientType { Linear, Radial, Sweep }
enum class Depth(val label: String) { Flat("Flat"), Emboss("Emboss"), Engrave("Engrave"), Extrude("3D") }
enum class Align { Left, Center, Right, Justify }
enum class FoilType(val label: String) { None("None"), Gold("Gold"), Silver("Silver"), RoseGold("Rose gold"), Copper("Copper"), Holo("Holographic") }
enum class LightType(val label: String) { None("None"), Window("Window"), Lamp("Lamp"), Spotlight("Spotlight") }

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
    Glass("Glass card"),
}

enum class FrameStyle(val label: String) {
    None("None"), Thin("Thin line"), Bold("Bold line"), Double("Double"), Triple("Triple"),
    Rounded("Rounded"), RoundedDouble("Soft double"), Dashed("Dashed"), Dotted("Dotted"), Stitched("Stitched"),
    Brackets("Corner brackets"), Notched("Cut corners"), Scallop("Scallop"), Zigzag("Zigzag"), Ticket("Ticket"),
    Stamp("Postage stamp"), Polaroid("Polaroid"), Mat("Gallery mat"), Bevel("Metal bevel"), Film("Film strip"),
    Checker("Checkered"), ArtDeco("Art deco"), Vintage("Vintage"), Neon("Neon glow"), Shadow("Offset shadow"),
    Sketch("Hand drawn"), Floral("Floral corners"), Hearts("Hearts"), Stars("Stars"), PhotoCorners("Photo corners"), Tape("Washi tape"),
}

enum class StickerType(val label: String, val symbol: String) {
    QuoteOpen("Open quote", "“"),
    QuoteClose("Close quote", "”"),
    Line("Line", "—"),
    DoubleLine("Double line", "="),
    Frame("Frame", "□"),
    Corners("Corners", "⌜"),
    Tape("Tape", "▭"),
    Star("Star", "★"),
    Heart("Heart", "♥"),
    Sparkle("Sparkle", "✦"),
    Squiggle("Squiggle", "∿"),
    Arrow("Arrow", "→"),
    Ring("Ring", "○"),
    Dots("Dots", "⋯"),
}

data class Sticker(
    val id: String = "",
    val type: StickerType = StickerType.Star,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = 0.2f,
    val rot: Float = 0f,
    val color: Int = c(0xFFFFFF),
    val alpha: Float = 1f,
)

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
    val foil: FoilType = FoilType.None,
    val bold: Boolean = false,
    val uppercase: Boolean = false,
    val align: Align = Align.Left,
    val margin: Float = 0.07f,
    val lineSpacing: Float = 1.0f,
    val letterSpacing: Float = 0f,
    val fillHeight: Boolean = true,
    val textScale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,

    // text outline shape
    val randomSize: Float = 0f,
    val randomSeed: Int = 1,
    val textShape: TextShape = TextShape.Rect,
    val shapeColor: Int? = null,

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

    // paper damage
    val stains: Boolean = false,
    val folds: Boolean = false,
    val burnt: Boolean = false,
    val torn: Boolean = false,
    val blots: Boolean = false,
    val wear: Float = 0.7f,

    // scene
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val light: LightType = LightType.None,
    val lightStrength: Float = 0.5f,

    val stickers: List<Sticker> = emptyList(),

    // frame around the whole picture
    val frame: FrameStyle = FrameStyle.None,
    val frameColor: Int = c(0xFFFFFF),
    val frameColor2: Int = c(0x1A1A1A),
    val frameThickness: Float = 0.4f,
    val frameInset: Float = 0.25f,
    val bg: Background = Background(),
)
