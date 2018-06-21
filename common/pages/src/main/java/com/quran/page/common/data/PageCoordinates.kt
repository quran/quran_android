package com.quran.page.common.data

import android.graphics.RectF

data class PageCoordinates(val page: Int,
                           val pageBounds: RectF,
                           val suraHeaders: List<SuraHeaderLocation>,
                           val ayahMarkers: List<AyahMarkerLocation>)
