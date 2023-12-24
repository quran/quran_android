package com.quran.page.common.data

import com.quran.page.common.data.coordinates.PageGlyphsCoords

data class AyahCoordinates(val page: Int,
                           val ayahCoordinates: Map<String, List<AyahBounds>>,
                           val glyphCoordinates: PageGlyphsCoords?)
