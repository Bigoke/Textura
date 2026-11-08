package com.textura.app.model

import android.content.Context
import android.graphics.Typeface

enum class FontGroup(val label: String) {
    Handwritten("Handwritten"),
    Script("Script"),
    Typed("Typed"),
    Serif("Serif"),
    Sans("Sans"),
    Display("Display"),
}

data class AppFont(val id: String, val label: String, val group: FontGroup) {
    val assetPath: String get() = "fonts/$id.ttf"
}

object Fonts {
    val all: List<AppFont> = listOf(
        // handwriting
        AppFont("caveat", "Caveat", FontGroup.Handwritten),
        AppFont("shadows_into_light", "Shadows Into Light", FontGroup.Handwritten),
        AppFont("indie_flower", "Indie Flower", FontGroup.Handwritten),
        AppFont("patrick_hand", "Patrick Hand", FontGroup.Handwritten),
        AppFont("reenie_beanie", "Reenie Beanie", FontGroup.Handwritten),
        AppFont("homemade_apple", "Homemade Apple", FontGroup.Handwritten),
        AppFont("gloria_hallelujah", "Gloria Hallelujah", FontGroup.Handwritten),
        AppFont("kalam", "Kalam", FontGroup.Handwritten),
        AppFont("covered_by_your_grace", "Covered By Your Grace", FontGroup.Handwritten),
        AppFont("permanent_marker", "Permanent Marker", FontGroup.Handwritten),
        AppFont("rock_salt", "Rock Salt", FontGroup.Handwritten),
        // script / calligraphy
        AppFont("dancing_script", "Dancing Script", FontGroup.Script),
        AppFont("great_vibes", "Great Vibes", FontGroup.Script),
        AppFont("sacramento", "Sacramento", FontGroup.Script),
        AppFont("satisfy", "Satisfy", FontGroup.Script),
        AppFont("pacifico", "Pacifico", FontGroup.Script),
        AppFont("yellowtail", "Yellowtail", FontGroup.Script),
        AppFont("lobster", "Lobster", FontGroup.Script),
        // typed
        AppFont("special_elite", "Special Elite", FontGroup.Typed),
        AppFont("courier_prime", "Courier Prime", FontGroup.Typed),
        AppFont("space_mono", "Space Mono", FontGroup.Typed),
        AppFont("vt323", "VT323", FontGroup.Typed),
        AppFont("cutive_mono", "Cutive Mono", FontGroup.Typed),
        // serif
        AppFont("playfair", "Playfair Display", FontGroup.Serif),
        AppFont("lora", "Lora", FontGroup.Serif),
        AppFont("eb_garamond", "EB Garamond", FontGroup.Serif),
        AppFont("crimson_text", "Crimson Text", FontGroup.Serif),
        AppFont("cinzel", "Cinzel", FontGroup.Serif),
        AppFont("old_standard", "Old Standard", FontGroup.Serif),
        AppFont("unifraktur", "UnifrakturCook", FontGroup.Serif),
        // sans
        AppFont("montserrat", "Montserrat", FontGroup.Sans),
        AppFont("poppins", "Poppins", FontGroup.Sans),
        AppFont("oswald", "Oswald", FontGroup.Sans),
        // display
        AppFont("abril_fatface", "Abril Fatface", FontGroup.Display),
        AppFont("bebas_neue", "Bebas Neue", FontGroup.Display),
        AppFont("anton", "Anton", FontGroup.Display),
        AppFont("archivo_black", "Archivo Black", FontGroup.Display),
        AppFont("bungee", "Bungee", FontGroup.Display),
        AppFont("righteous", "Righteous", FontGroup.Display),
        AppFont("monoton", "Monoton", FontGroup.Display),
    )

    fun byId(id: String): AppFont = all.firstOrNull { it.id == id } ?: all.first { it.id == "montserrat" }

    private val cache = HashMap<String, Typeface>()

    @Synchronized
    fun typeface(context: Context, id: String): Typeface {
        val font = byId(id)
        return cache.getOrPut(font.id) {
            runCatching { Typeface.createFromAsset(context.applicationContext.assets, font.assetPath) }
                .getOrDefault(Typeface.DEFAULT)
        }
    }
}
