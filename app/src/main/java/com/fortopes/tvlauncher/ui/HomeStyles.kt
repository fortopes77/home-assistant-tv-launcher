package com.fortopes.tvlauncher.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.fortopes.tvlauncher.R
import com.fortopes.tvlauncher.data.HaEntity

/** How a Home row card looks: its symbol and background colours. */
data class CardStyle(@DrawableRes val icon: Int, val colors: List<Color>)

private val SUNRISE = listOf(Color(0xFF3B1E5C), Color(0xFFD9485F), Color(0xFFFFA94D), Color(0xFFFFD66B))
private val NIGHT = listOf(Color(0xFF0B1030), Color(0xFF1E2A6E), Color(0xFF4B3A8C))
private val CINEMA = listOf(Color(0xFF120406), Color(0xFF5C0A14), Color(0xFFB0182A))
private val LIGHTS_UP = listOf(Color(0xFF3A2410), Color(0xFFC77A1E), Color(0xFFFFC56B))
private val EVENING = listOf(Color(0xFF2A1035), Color(0xFF8A2D6B), Color(0xFFE0784A))
private val AWAY = listOf(Color(0xFF10232B), Color(0xFF1F5866), Color(0xFF3FA3A8))
private val HOME = listOf(Color(0xFF13261A), Color(0xFF2E6B3F), Color(0xFF7BC47F))
private val PARTY = listOf(Color(0xFF2B0B4A), Color(0xFFB0249C), Color(0xFF29B6F6))

/** A light that's on glows warm; a blind that's open shows a daytime sky. Off or closed stay dark. */
private val LAMP = listOf(Color(0xFF5A3A12), Color(0xFFD08A2A), Color(0xFFFFD27A))
private val SKY = listOf(Color(0xFF1C4E80), Color(0xFF4A90C8), Color(0xFFA9D6F5))

// Words in a scene or script's name, checked in order (so "End movie mode" is lights up, not cinema).
private val THEMES: List<Pair<List<String>, CardStyle>> = listOf(
    listOf("end movie", "movie end", "lights up", "intermission") to CardStyle(R.drawable.ic_bulb, LIGHTS_UP),
    listOf("morning", "wake", "sunrise", "breakfast") to CardStyle(R.drawable.ic_sunrise, SUNRISE),
    listOf("night", "sleep", "bed", "bedtime", "goodnight", "lights out") to CardStyle(R.drawable.ic_moon, NIGHT),
    listOf("movie", "cinema", "film", "tv time", "theatre", "theater") to CardStyle(R.drawable.ic_movie, CINEMA),
    listOf("evening", "relax", "chill", "dinner", "sunset", "cosy", "cozy") to CardStyle(R.drawable.ic_sunrise, EVENING),
    listOf("leave", "away", "goodbye", "going out") to CardStyle(R.drawable.ic_leave, AWAY),
    listOf("arrive", "home", "welcome") to CardStyle(R.drawable.ic_home_glyph, HOME),
    listOf("party", "music", "disco") to CardStyle(R.drawable.ic_music, PARTY),
)

/** A scene or script's look, from its name. Anything else gets colours made from its name, so each differs. */
fun actionStyle(entity: HaEntity): CardStyle {
    val name = entity.name.lowercase()
    THEMES.firstOrNull { (words, _) -> words.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(name) } }
        ?.let { return it.second }
    var h = 0
    for (c in name) h = (h * 31 + c.code) % 360
    val a = Color.hsv(h.toFloat(), 0.6f, 0.30f)
    val b = Color.hsv(((h + 40) % 360).toFloat(), 0.65f, 0.65f)
    return CardStyle(R.drawable.ic_play, listOf(a, b))
}

/** A light or blind's look: warm when a light is on, sky when a blind is open, dark otherwise. */
fun controlStyle(entity: HaEntity): CardStyle =
    if (entity.domain == "cover") CardStyle(R.drawable.ic_blind, if (entity.isOn) SKY else OFF)
    else CardStyle(R.drawable.ic_bulb, if (entity.isOn) LAMP else OFF)

private val OFF = listOf(Color(0xFF262838), Color(0xFF15161D))
