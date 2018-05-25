package com.quran.labs.androidquran.data

import com.quran.labs.androidquran.common.AyahBounds

data class PageCoordinates(val page: Int,
                           val ayahCoordinates: Map<String, List<AyahBounds>>,
                           val suraHeaders: List<SuraHeaderLocation>,
                           val ayahMarkers: List<AyahMarkerLocation>)
