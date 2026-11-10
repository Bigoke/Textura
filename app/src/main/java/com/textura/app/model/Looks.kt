package com.textura.app.model

private fun solid(rgb: Int) = Background(BgKind.Solid, listOf(c(rgb)))

private fun grad(angle: Float, vararg rgb: Int, type: GradientType = GradientType.Linear) =
    Background(BgKind.Gradient, rgb.map { c(it) }, type, angle)

/** The built-in template catalogue. Each look carries its own font, background, paper and depth style. */
object Catalogue {

    val all: List<Look> = listOf(
        // ---------------------------------------------------------------- PAPER
        Look(
            id = "paper_notebook", name = "Notebook", category = Category.Paper,
            fontId = "caveat", textColor = c(0x1F3A93), handwritten = true, imperfection = 0.7f,
            lineSpacing = 1.0f, margin = 0.05f, fillHeight = false,
            paper = PaperType.Lined, paperColor = c(0xFBF8EF), bg = solid(0x2B2B33),
        ),
        Look(
            id = "paper_desk_notebook", name = "On the desk", category = Category.Paper,
            fontId = "kalam", textColor = c(0x22304F), handwritten = true, imperfection = 0.6f,
            margin = 0.05f, fillHeight = false,
            paper = PaperType.Lined, paperColor = c(0xFFFDF4), paperInset = 0.07f, tilt = -2.5f,
            bg = grad(60f, 0x6B4A2E, 0x8A6140, 0x5A3C24),
        ),
        Look(
            id = "paper_graph", name = "Graph paper", category = Category.Paper,
            fontId = "reenie_beanie", textColor = c(0x2B2B33), handwritten = true, imperfection = 0.6f,
            lineSpacing = 0.95f, margin = 0.06f,
            paper = PaperType.Graph, paperColor = c(0xF7FAFC), bg = solid(0x1E2A3A),
        ),
        Look(
            id = "paper_dotted", name = "Dot journal", category = Category.Paper,
            fontId = "patrick_hand", textColor = c(0x3B3B44), handwritten = true, imperfection = 0.5f,
            margin = 0.07f, paper = PaperType.Dotted, paperColor = c(0xF3EFE6), bg = solid(0x25252B),
        ),
        Look(
            id = "paper_parchment", name = "Parchment", category = Category.Paper,
            fontId = "eb_garamond", textColor = c(0x3A2412), align = Align.Justify, margin = 0.09f,
            paper = PaperType.Parchment, paperColor = c(0xE8D5A8), grain = 0.7f, bg = solid(0x241A10),
        ),
        Look(
            id = "paper_old_letter", name = "Old letter", category = Category.Paper,
            fontId = "homemade_apple", textColor = c(0x2C1B0E), handwritten = true, imperfection = 0.8f,
            lineSpacing = 1.1f, margin = 0.08f, fillHeight = false,
            paper = PaperType.Letter, paperColor = c(0xE6D2A2), paperInset = 0.05f, tilt = 1.8f,
            bg = grad(90f, 0x3B2A1B, 0x1E150D),
        ),
        Look(
            id = "paper_sticky", name = "Sticky note", category = Category.Paper,
            fontId = "gloria_hallelujah", textColor = c(0x2B2B2B), handwritten = true, imperfection = 0.7f,
            align = Align.Center, margin = 0.1f,
            paper = PaperType.Sticky, paperColor = c(0xFFE76B), paperInset = 0.06f, tilt = 3f,
            bg = grad(45f, 0x30303A, 0x1C1C22),
        ),
        Look(
            id = "paper_sticky_pink", name = "Pink note", category = Category.Paper,
            fontId = "indie_flower", textColor = c(0x4A1030), handwritten = true, imperfection = 0.6f,
            align = Align.Center, margin = 0.1f,
            paper = PaperType.Sticky, paperColor = c(0xFF9CC8), paperInset = 0.06f, tilt = -3f,
            bg = grad(45f, 0x2A2A44, 0x14142A),
        ),
        Look(
            id = "paper_receipt", name = "Receipt", category = Category.Paper,
            fontId = "courier_prime", textColor = c(0x2A2A2A), imperfection = 0.4f, margin = 0.08f,
            paper = PaperType.Receipt, paperColor = c(0xFAFAF4), paperInset = 0.1f, tilt = 0f,
            bg = grad(90f, 0x24242C, 0x15151A),
        ),
        Look(
            id = "paper_kraft", name = "Kraft", category = Category.Paper,
            fontId = "permanent_marker", textColor = c(0x1B1B1B), handwritten = true, imperfection = 0.4f,
            align = Align.Center, margin = 0.08f,
            paper = PaperType.Kraft, paperColor = c(0xB98B5E), bg = solid(0x2A1F16),
        ),
        Look(
            id = "paper_chalkboard", name = "Chalkboard", category = Category.Paper,
            fontId = "shadows_into_light", textColor = c(0xF4F1E8), handwritten = true, imperfection = 0.8f,
            align = Align.Center, margin = 0.06f,
            paper = PaperType.Chalkboard, paperColor = c(0x2F4F43), bg = solid(0x111111),
        ),
        Look(
            id = "paper_newsprint", name = "Newsprint", category = Category.Paper,
            fontId = "old_standard", textColor = c(0x1E1E1E), align = Align.Justify, margin = 0.06f,
            imperfection = 0.3f, lineSpacing = 0.95f,
            paper = PaperType.Newsprint, paperColor = c(0xE4DFD3), bg = solid(0x2B2B2B),
        ),

        // ---------------------------------------------------------------- TYPED
        Look(
            id = "typed_typewriter", name = "Typewriter", category = Category.Typed,
            fontId = "special_elite", textColor = c(0x24201C), imperfection = 0.8f, margin = 0.08f,
            lineSpacing = 1.05f, paper = PaperType.Parchment, paperColor = c(0xF2EBDD), grain = 0.45f,
            bg = solid(0x1F1F24),
        ),
        Look(
            id = "typed_manuscript", name = "Manuscript", category = Category.Typed,
            fontId = "courier_prime", textColor = c(0x111111), imperfection = 0.25f, margin = 0.1f,
            lineSpacing = 1.2f, fillHeight = false,
            paper = PaperType.Lined, paperColor = c(0xFFFFFF), bg = solid(0x202026),
        ),
        Look(
            id = "typed_terminal", name = "Terminal", category = Category.Typed,
            fontId = "vt323", textColor = c(0x39FF7A), margin = 0.06f, lineSpacing = 1.0f,
            shadow = true, shadowColor = ca(190, 0x39FF7A), shadowBlur = 0.2f, shadowOffset = 0f,
            bg = solid(0x05100A),
        ),
        Look(
            id = "typed_amber", name = "Amber screen", category = Category.Typed,
            fontId = "space_mono", textColor = c(0xFFB000), margin = 0.07f,
            shadow = true, shadowColor = ca(170, 0xFFB000), shadowBlur = 0.18f, shadowOffset = 0f,
            bg = grad(0f, 0x1A1000, 0x0B0700, type = GradientType.Radial),
        ),
        Look(
            id = "typed_newspaper", name = "Headline", category = Category.Typed,
            fontId = "playfair", textColor = c(0x151515), align = Align.Justify, margin = 0.06f,
            bold = true, lineSpacing = 0.95f,
            paper = PaperType.Newsprint, paperColor = c(0xEAE6DA), bg = solid(0x202020),
        ),
        Look(
            id = "typed_book", name = "Book page", category = Category.Typed,
            fontId = "crimson_text", textColor = c(0x1D1A17), align = Align.Justify, margin = 0.11f,
            lineSpacing = 1.1f, paper = PaperType.Parchment, paperColor = c(0xF4EEDF), grain = 0.3f,
            bg = solid(0x1B1B1F),
        ),
        Look(
            id = "typed_courier_dark", name = "Screenplay", category = Category.Typed,
            fontId = "cutive_mono", textColor = c(0xECE7DB), margin = 0.09f, lineSpacing = 1.15f,
            imperfection = 0.3f, bg = grad(90f, 0x22262E, 0x14161B),
        ),

        // ---------------------------------------------------------------- HANDWRITTEN
        Look(
            id = "hand_ink_letter", name = "Ink letter", category = Category.Handwritten,
            fontId = "homemade_apple", textColor = c(0x1B2A5E), handwritten = true, imperfection = 0.9f,
            lineSpacing = 1.1f, margin = 0.08f, fillHeight = false,
            paper = PaperType.Parchment, paperColor = c(0xF1E6C8), grain = 0.55f, bg = solid(0x2A2018),
        ),
        Look(
            id = "hand_pencil", name = "Pencil", category = Category.Handwritten,
            fontId = "reenie_beanie", textColor = ca(225, 0x3A3A3F), handwritten = true, imperfection = 1f,
            lineSpacing = 1.0f, margin = 0.07f,
            paper = PaperType.Lined, paperColor = c(0xF8F5EC), bg = solid(0x26262C),
        ),
        Look(
            id = "hand_marker", name = "Marker", category = Category.Handwritten,
            fontId = "permanent_marker", textColor = c(0x111118), handwritten = true, imperfection = 0.5f,
            align = Align.Center, margin = 0.07f, bg = solid(0xFFD84D),
        ),
        Look(
            id = "hand_marker_red", name = "Red marker", category = Category.Handwritten,
            fontId = "rock_salt", textColor = c(0xE0312B), handwritten = true, imperfection = 0.6f,
            align = Align.Center, margin = 0.08f, bg = solid(0xF5F1E8),
        ),
        Look(
            id = "hand_calligraphy", name = "Calligraphy", category = Category.Handwritten,
            fontId = "great_vibes", textColor = c(0x3A1D12), align = Align.Center, margin = 0.1f,
            imperfection = 0.15f, lineSpacing = 1.05f,
            paper = PaperType.Parchment, paperColor = c(0xF2E7CC), grain = 0.4f, bg = solid(0x1F1712),
        ),
        Look(
            id = "hand_script_gold", name = "Gold script", category = Category.Handwritten,
            fontId = "sacramento", textColor = c(0xF3D27A), textColor2 = c(0xB8862B), align = Align.Center,
            margin = 0.09f, lineSpacing = 0.9f, bg = grad(90f, 0x1A1A24, 0x0B0B10),
        ),
        Look(
            id = "hand_diary", name = "Diary", category = Category.Handwritten,
            fontId = "covered_by_your_grace", textColor = c(0x5A2A4A), handwritten = true, imperfection = 0.8f,
            margin = 0.07f, fillHeight = false,
            paper = PaperType.Lined, paperColor = c(0xFFF3F6), bg = solid(0x2A1F28),
        ),
        Look(
            id = "hand_pen_blue", name = "Blue pen", category = Category.Handwritten,
            fontId = "dancing_script", textColor = c(0x1F4BC0), handwritten = true, imperfection = 0.6f,
            margin = 0.07f, lineSpacing = 1.0f,
            paper = PaperType.Lined, paperColor = c(0xFFFFFF), bg = solid(0x1F2430),
        ),

        // ---------------------------------------------------------------- ART
        Look(
            id = "art_poster", name = "Poster", category = Category.Art,
            fontId = "anton", textColor = c(0xFFF4E0), uppercase = true, align = Align.Justify, margin = 0.06f,
            lineSpacing = 0.95f, bg = grad(135f, 0xFF4E50, 0xF9D423),
        ),
        Look(
            id = "art_quote_card", name = "Quote card", category = Category.Art,
            fontId = "playfair", textColor = c(0xFFFFFF), align = Align.Center, margin = 0.1f, lineSpacing = 1.05f,
            bg = grad(160f, 0x232526, 0x414345),
        ),
        Look(
            id = "art_sunset", name = "Sunset", category = Category.Art,
            fontId = "montserrat", textColor = c(0xFFFFFF), bold = true, align = Align.Center, margin = 0.1f,
            shadow = true, shadowColor = ca(90, 0x3A0A2A), shadowBlur = 0.1f, shadowOffset = 0.05f,
            bg = grad(180f, 0xFF512F, 0xDD2476, 0x4A1B6B),
        ),
        Look(
            id = "art_ocean", name = "Ocean", category = Category.Art,
            fontId = "oswald", textColor = c(0xE9FBFF), uppercase = true, align = Align.Justify, margin = 0.07f,
            bg = grad(200f, 0x0F2027, 0x203A43, 0x2C7A94),
        ),
        Look(
            id = "art_neon", name = "Neon", category = Category.Art,
            fontId = "righteous", textColor = c(0xFFFFFF), align = Align.Center, margin = 0.09f,
            outline = true, outlineColor = c(0xFF2BD6), outlineWidth = 0.035f,
            shadow = true, shadowColor = ca(235, 0xFF2BD6), shadowBlur = 0.28f, shadowOffset = 0f,
            bg = solid(0x0A0612),
        ),
        Look(
            id = "art_neon_blue", name = "Neon blue", category = Category.Art,
            fontId = "bungee", textColor = c(0xE8FFFF), align = Align.Center, margin = 0.08f,
            shadow = true, shadowColor = ca(240, 0x16D9FF), shadowBlur = 0.3f, shadowOffset = 0f,
            bg = grad(0f, 0x0A1A33, 0x03060F, type = GradientType.Radial),
        ),
        Look(
            id = "art_minimal", name = "Minimal", category = Category.Art,
            fontId = "poppins", textColor = c(0x1A1A1A), align = Align.Left, margin = 0.1f,
            lineSpacing = 1.1f, bg = solid(0xF4F1EA),
        ),
        Look(
            id = "art_mono_black", name = "Black & white", category = Category.Art,
            fontId = "archivo_black", textColor = c(0xFFFFFF), uppercase = true, align = Align.Justify,
            margin = 0.05f, lineSpacing = 0.92f, bg = solid(0x000000),
        ),
        Look(
            id = "art_aurora", name = "Aurora", category = Category.Art,
            fontId = "abril_fatface", textColor = c(0xFFFFFF), align = Align.Center, margin = 0.09f,
            bg = grad(0f, 0x0F0C29, 0x302B63, 0x24C6DC, 0x514A9D, type = GradientType.Sweep),
        ),
        Look(
            id = "art_pastel", name = "Pastel", category = Category.Art,
            fontId = "lobster", textColor = c(0x5A3E6B), align = Align.Center, margin = 0.1f,
            bg = grad(135f, 0xFFD6E8, 0xD6E4FF, 0xE4FFD9),
        ),
        Look(
            id = "art_gothic", name = "Gothic", category = Category.Art,
            fontId = "unifraktur", textColor = c(0xE8E1D2), align = Align.Center, margin = 0.1f,
            bg = grad(0f, 0x2A0A0A, 0x050202, type = GradientType.Radial),
        ),
        Look(
            id = "art_cinema", name = "Cinema", category = Category.Art,
            fontId = "cinzel", textColor = c(0xF2E7C9), textColor2 = c(0xC9A24B), align = Align.Center,
            margin = 0.1f, bg = grad(90f, 0x050505, 0x15161B),
        ),

        Look(
            id = "art_word_cloud", name = "Word cloud", category = Category.Art,
            fontId = "montserrat", textColor = c(0xFFFFFF), textColor2 = c(0xFFD166), bold = true, align = Align.Justify,
            margin = 0.06f, lineSpacing = 0.95f, randomSize = 0.9f, randomSeed = 3,
            bg = grad(140f, 0x1B1F3B, 0x6A3D9A),
        ),
        Look(
            id = "art_chaos", name = "Chaos type", category = Category.Art,
            fontId = "anton", textColor = c(0x111111), uppercase = true, align = Align.Justify, margin = 0.05f,
            lineSpacing = 0.9f, randomSize = 1f, randomSeed = 11, bg = solid(0xF2E8D5),
        ),
        Look(
            id = "art_ransom", name = "Mixed sizes", category = Category.Art,
            fontId = "playfair", textColor = c(0xFFF3E0), textColor2 = c(0xE8604C), align = Align.Center, margin = 0.08f,
            randomSize = 0.7f, randomSeed = 5, bg = grad(160f, 0x2B1B17, 0x5C3A2E),
        ),

        // ---------------------------------------------------------------- 3D
        Look(
            id = "d3_block_gold", name = "Gold blocks", category = Category.ThreeD,
            fontId = "anton", textColor = c(0xFFE08A), textColor2 = c(0xE0A52B), uppercase = true,
            align = Align.Justify, margin = 0.08f, lineSpacing = 1.05f,
            depth = Depth.Extrude, depthLength = 0.55f, depthAngle = 50f, sideColor = c(0x9A6A12),
            bg = grad(90f, 0x1B1B28, 0x0A0A12),
        ),
        Look(
            id = "d3_candy", name = "Candy 3D", category = Category.ThreeD,
            fontId = "bungee", textColor = c(0xFF6FB5), align = Align.Center, margin = 0.09f,
            depth = Depth.Extrude, depthLength = 0.6f, depthAngle = 60f, sideColor = c(0xB5246E),
            bg = grad(135f, 0xFFE7F2, 0xC9E4FF),
        ),
        Look(
            id = "d3_retro", name = "Retro", category = Category.ThreeD,
            fontId = "abril_fatface", textColor = c(0xFFF2D6), align = Align.Center, margin = 0.1f,
            depth = Depth.Extrude, depthLength = 0.5f, depthAngle = 35f, sideColor = c(0xE0482F), gradientSide = false,
            bg = grad(180f, 0x2B6F8F, 0x1B3F5A),
        ),
        Look(
            id = "d3_chrome", name = "Chrome", category = Category.ThreeD,
            fontId = "archivo_black", textColor = c(0xF5F7FA), textColor2 = c(0x8E99A8), uppercase = true,
            align = Align.Center, margin = 0.08f,
            depth = Depth.Extrude, depthLength = 0.4f, depthAngle = 45f, sideColor = c(0x4A5463),
            bg = grad(90f, 0x1C2230, 0x090B10),
        ),
        Look(
            id = "d3_neon_slab", name = "Neon slab", category = Category.ThreeD,
            fontId = "righteous", textColor = c(0xFFFFFF), align = Align.Center, margin = 0.09f,
            depth = Depth.Extrude, depthLength = 0.5f, depthAngle = 40f, sideColor = c(0x7A2BFF),
            bg = grad(160f, 0x120A2E, 0x050212),
        ),
        Look(
            id = "d3_paper_emboss", name = "Embossed paper", category = Category.ThreeD,
            fontId = "playfair", textColor = c(0xEFE8DA), bold = true, align = Align.Center, margin = 0.1f,
            depth = Depth.Emboss, paper = PaperType.Parchment, paperColor = c(0xEFE8DA), grain = 0.35f,
            bg = solid(0x1D1D22),
        ),
        Look(
            id = "d3_engraved", name = "Engraved", category = Category.ThreeD,
            fontId = "cinzel", textColor = c(0x8A8F98), bold = true, align = Align.Center, margin = 0.1f,
            depth = Depth.Engrave, bg = grad(135f, 0xC9CED6, 0xA6ACB6),
        ),
        Look(
            id = "d3_stone", name = "Stone", category = Category.ThreeD,
            fontId = "old_standard", textColor = c(0xB9B3A6), bold = true, align = Align.Justify, margin = 0.08f,
            depth = Depth.Engrave, paper = PaperType.Newsprint, paperColor = c(0xA8A298), grain = 1f,
            bg = solid(0x2A2926),
        ),
        Look(
            id = "d3_long_shadow", name = "Long shadow", category = Category.ThreeD,
            fontId = "oswald", textColor = c(0xFFFFFF), bold = true, uppercase = true, align = Align.Justify,
            margin = 0.07f, depth = Depth.Extrude, depthLength = 1f, depthAngle = 45f, sideColor = c(0x2A9D8F),
            gradientSide = true, highlight = false, bg = solid(0x264653),
        ),

        // ---------------------------------------------------------------- SHAPES
        Look(
            id = "shape_heart", name = "Love heart", category = Category.Shapes,
            fontId = "pacifico", textColor = c(0xFFFFFF), align = Align.Center, margin = 0.06f, lineSpacing = 1.0f,
            textShape = TextShape.Heart, shapeColor = c(0xE8456B), bg = grad(160f, 0xFFE3EC, 0xFFC2D6),
        ),
        Look(
            id = "shape_circle_badge", name = "Round badge", category = Category.Shapes,
            fontId = "oswald", textColor = c(0x1A1A1A), bold = true, uppercase = true, align = Align.Center, margin = 0.05f,
            textShape = TextShape.Circle, shapeColor = c(0xFFD84D), bg = solid(0x1B1B22),
        ),
        Look(
            id = "shape_oval_cameo", name = "Cameo", category = Category.Shapes,
            fontId = "eb_garamond", textColor = c(0x3A2412), align = Align.Center, margin = 0.06f,
            textShape = TextShape.Oval, shapeColor = c(0xF1E6C8), bg = grad(160f, 0x6B1D2A, 0x2E0A12),
        ),
        Look(
            id = "shape_bubble", name = "Speech bubble", category = Category.Shapes,
            fontId = "poppins", textColor = c(0x1B2A4A), align = Align.Center, margin = 0.08f,
            textShape = TextShape.Bubble, shapeColor = c(0xFFFFFF), bg = grad(150f, 0x4776E6, 0x8E54E9),
        ),
        Look(
            id = "shape_arch", name = "Arch window", category = Category.Shapes,
            fontId = "cinzel", textColor = c(0x4A3626), align = Align.Center, margin = 0.06f,
            textShape = TextShape.Arch, shapeColor = c(0xEFE0CC), bg = solid(0xC9A68A),
        ),
        Look(
            id = "shape_diamond", name = "Diamond", category = Category.Shapes,
            fontId = "bebas_neue", textColor = c(0xFFFFFF), align = Align.Center, margin = 0.05f, uppercase = true,
            textShape = TextShape.Diamond, shapeColor = c(0x16A39A), bg = solid(0x0F1F26),
        ),
        Look(
            id = "shape_hexagon", name = "Hexagon", category = Category.Shapes,
            fontId = "righteous", textColor = c(0xFFFFFF), align = Align.Center, margin = 0.05f,
            textShape = TextShape.Hexagon, shapeColor = c(0x7A2BFF), bg = grad(160f, 0x1A0F3A, 0x07030F),
        ),
        Look(
            id = "shape_star_poster", name = "Star", category = Category.Shapes,
            fontId = "anton", textColor = c(0x1A1A1A), uppercase = true, align = Align.Center, margin = 0.04f,
            textShape = TextShape.Star, shapeColor = c(0xFFE76B), bg = grad(150f, 0xFF4E50, 0xAA1E5A),
        ),

        // ---------------------------------------------------------------- FOIL & GLASS
        Look(
            id = "foil_gold", name = "Gold foil", category = Category.Art,
            fontId = "playfair", foil = FoilType.Gold, bold = true, align = Align.Center, margin = 0.12f,
            bg = grad(90f, 0x111116, 0x050507),
            stickers = listOf(Sticker("f1", StickerType.Frame, 0.5f, 0.5f, 0.9f, 0f, c(0xD9A521), 0.9f)),
        ),
        Look(
            id = "foil_rose", name = "Rose gold", category = Category.Art,
            fontId = "dancing_script", foil = FoilType.RoseGold, bold = true, align = Align.Center, margin = 0.1f,
            bg = grad(160f, 0x3A1530, 0x14061A),
        ),
        Look(
            id = "foil_silver", name = "Silver foil", category = Category.Art,
            fontId = "archivo_black", foil = FoilType.Silver, uppercase = true, align = Align.Justify, margin = 0.07f,
            lineSpacing = 0.95f, bg = grad(160f, 0x0F2040, 0x050B1A),
        ),
        Look(
            id = "foil_holo", name = "Holographic", category = Category.Art,
            fontId = "bungee", foil = FoilType.Holo, align = Align.Center, margin = 0.09f,
            shadow = true, shadowColor = ca(150, 0x7873F5), shadowBlur = 0.25f, shadowOffset = 0f,
            bg = grad(0f, 0x16122E, 0x050410, type = GradientType.Radial),
        ),
        Look(
            id = "glass_card", name = "Frosted glass", category = Category.Art,
            fontId = "poppins", textColor = c(0xFFFFFF), align = Align.Center, margin = 0.09f,
            paper = PaperType.Glass, paperColor = ca(70, 0xFFFFFF), paperInset = 0.08f,
            bg = grad(150f, 0xFF512F, 0xDD2476, 0x4A1B6B, 0x1B6BC9),
        ),
        Look(
            id = "glass_dark", name = "Dark glass", category = Category.Art,
            fontId = "montserrat", textColor = c(0xF4F7FF), align = Align.Left, margin = 0.09f,
            paper = PaperType.Glass, paperColor = ca(105, 0x0B1020), paperInset = 0.07f,
            bg = grad(200f, 0x0F2027, 0x2C7A94, 0x16D9FF),
        ),

        // ---------------------------------------------------------------- SCENES & WEAR
        Look(
            id = "scene_desk", name = "Desk perspective", category = Category.Paper,
            fontId = "caveat", textColor = c(0x1F3A93), handwritten = true, imperfection = 0.7f, margin = 0.06f,
            fillHeight = false, paper = PaperType.Lined, paperColor = c(0xFFFDF4), paperInset = 0.07f, tilt = -2f,
            tiltX = 20f, tiltY = -14f, light = LightType.Window, lightStrength = 0.55f,
            bg = grad(60f, 0x6B4A2E, 0x8A6140, 0x4A301C),
        ),
        Look(
            id = "scene_lamp_letter", name = "Lamplight letter", category = Category.Paper,
            fontId = "great_vibes", textColor = c(0x2C1B0E), handwritten = true, imperfection = 0.5f,
            lineSpacing = 1.05f, margin = 0.09f, fillHeight = false, align = Align.Center,
            paper = PaperType.Letter, paperColor = c(0xE6D2A2), paperInset = 0.05f, tilt = 1.5f,
            light = LightType.Lamp, lightStrength = 0.75f, folds = true,
            bg = grad(90f, 0x2A1A10, 0x0D0805),
        ),
        Look(
            id = "scene_spotlight", name = "Spotlight", category = Category.Art,
            fontId = "anton", textColor = c(0xFFFFFF), uppercase = true, align = Align.Center, margin = 0.09f,
            light = LightType.Spotlight, lightStrength = 0.85f, bg = grad(90f, 0x20202C, 0x0A0A10),
        ),
        Look(
            id = "wear_burnt_scroll", name = "Burnt scroll", category = Category.Paper,
            fontId = "eb_garamond", textColor = c(0x2A1608), align = Align.Justify, margin = 0.09f,
            paper = PaperType.Parchment, paperColor = c(0xE0C48C), paperInset = 0.06f, grain = 0.8f,
            burnt = true, torn = true, stains = true, wear = 0.9f, bg = solid(0x1A120C),
        ),
        Look(
            id = "wear_coffee_note", name = "Coffee note", category = Category.Paper,
            fontId = "kalam", textColor = c(0x22304F), handwritten = true, imperfection = 0.6f, margin = 0.06f,
            fillHeight = false, paper = PaperType.Lined, paperColor = c(0xFBF8EF), paperInset = 0.05f, tilt = -1.5f,
            stains = true, folds = true, blots = true, bg = grad(60f, 0x5A3C24, 0x3A2616),
        ),
        Look(
            id = "wear_torn_page", name = "Torn page", category = Category.Paper,
            fontId = "reenie_beanie", textColor = c(0x2B2B33), handwritten = true, imperfection = 0.7f, margin = 0.08f,
            paper = PaperType.Graph, paperColor = c(0xF7FAFC), paperInset = 0.05f, tilt = 2.2f,
            torn = true, blots = true, bg = solid(0x26303D),
        ),
    )

    fun byId(id: String): Look? = all.firstOrNull { it.id == id }
}
