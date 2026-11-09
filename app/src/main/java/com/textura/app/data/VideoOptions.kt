package com.textura.app.data

enum class VideoStyle(val label: String) {
    Auto("Auto"), Typewriter("Typewriter"), Handwriting("Handwriting"), Words("Word by word"), Lines("Line by line"), Sweep("Sweep"),
}

enum class FxSound(val label: String) { Auto("Auto"), Keys("Typewriter keys"), Pen("Pen on paper"), Paper("Paper rustle"), Off("Off") }

enum class MusicChoice(val label: String) { None("None"), Ambient("Ambient"), Plucks("Gentle plucks"), Imported("My file") }

data class VideoOptions(
    val style: VideoStyle = VideoStyle.Auto,
    /** Writing time in seconds; 0 means automatic from the text length. */
    val seconds: Float = 0f,
    val hold: Float = 2f,
    val pen: Boolean = true,
    val intro: Boolean = false,
    val introText: String = "",
    val introSec: Float = 1.5f,
    val outro: Boolean = false,
    val outroText: String = "",
    val outroSec: Float = 2f,
    val loop: Boolean = false,
    val fps: Int = 24,
    val longEdge: Int = 1280,
    val fx: FxSound = FxSound.Auto,
    val fxVolume: Float = 0.8f,
    val music: MusicChoice = MusicChoice.None,
    val musicVolume: Float = 0.5f,
    val musicPath: String? = null,
    val gif: Boolean = false,
    val gifFps: Int = 12,
    val gifEdge: Int = 480,
)
