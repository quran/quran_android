package com.quran.page.common.data

data class PageCoordinates(val page: Int,
                           val ayahCoordinates: Map<String, List<AyahBounds>>,
                           val suraHeaders: List<SuraHeaderLocation>,
                           val ayahMarkers: List<AyahMarkerLocation>)
