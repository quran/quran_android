package com.quran.mobile.linebyline.data.model

import com.quran.data.di.QuranScope
import com.quran.mobile.linebyline.data.LineByLineAyahInfoDatabase
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import com.quran.mobile.linebyline.data.dao.AyahMarkerInfo
import com.quran.mobile.linebyline.data.dao.SuraHeader
import com.quran.mobile.linebyline.data.dao.WordHighlight
import com.quran.mobile.linebyline.data.dao.asAyahHighlight
import com.quran.mobile.linebyline.data.dao.asAyahMarker
import com.quran.mobile.linebyline.data.dao.asSuraHeader
import com.quran.mobile.linebyline.data.dao.asWordHighlight
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@QuranScope
class PageModel @Inject constructor(private val database: LineByLineAyahInfoDatabase) {

  suspend fun suraHeaders(page: Int): ImmutableList<SuraHeader> {
    return withContext(Dispatchers.IO) {
      database
        .suraHeaderQueries
        .selectByPage(page.toLong())
        .executeAsList()
        .map { it.asSuraHeader() }
        .toImmutableList()
    }
  }

  suspend fun ayahMarkers(page: Int): ImmutableList<AyahMarkerInfo> {
    return withContext(Dispatchers.IO) {
      database
        .ayahMarkerQueries
        .selectByPage(page.toLong())
        .executeAsList()
        .map { it.asAyahMarker() }
        .toImmutableList()
    }
  }

  suspend fun ayahHighlight(page: Int): ImmutableList<AyahHighlight> {
    return withContext(Dispatchers.IO) {
      database
        .ayahHighlightQueries
        .selectByPage(page.toLong())
        .executeAsList()
        .map { it.asAyahHighlight() }
        .toImmutableList()
    }
  }

  suspend fun wordHighlights(page: Int): ImmutableList<WordHighlight> {
    return withContext(Dispatchers.IO) {
      database.ayahGlyphsQueries
        .glyphsForPage(page.toLong())
        .executeAsList()
        .map { it.asWordHighlight() }
        .toImmutableList()
    }
  }
}
