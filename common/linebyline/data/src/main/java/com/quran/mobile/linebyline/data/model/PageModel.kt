package com.quran.mobile.linebyline.data.model

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quran.data.di.QuranScope
import com.quran.mobile.linebyline.data.Database
import com.quran.mobile.linebyline.data.dao.AyahHighlight
import com.quran.mobile.linebyline.data.dao.AyahMarkerInfo
import com.quran.mobile.linebyline.data.dao.SuraHeader
import com.quran.mobile.linebyline.data.dao.asAyahHighlight
import com.quran.mobile.linebyline.data.dao.asAyahMarker
import com.quran.mobile.linebyline.data.dao.asSuraHeader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@QuranScope
class PageModel @Inject constructor(private val database: Database) {

  fun suraHeaders(page: Int): Flow<ImmutableList<SuraHeader>> {
    return database
      .suraHeaderQueries
      .selectByPage(page.toLong())
      .asFlow()
      .mapToList(Dispatchers.IO)
      .map { list -> list.map { it.asSuraHeader() } }
      .map { it.toImmutableList() }
  }

  fun ayahMarkers(page: Int): Flow<ImmutableList<AyahMarkerInfo>> {
    return database
      .ayahMarkerQueries
      .selectByPage(page.toLong())
      .asFlow()
      .mapToList(Dispatchers.IO)
      .map { list -> list.map { it.asAyahMarker() } }
      .map { it.toImmutableList() }
  }

  fun ayahHighlight(page: Int): Flow<ImmutableList<AyahHighlight>> {
    return database
      .ayahHighlightQueries
      .selectByPage(page.toLong())
      .asFlow()
      .mapToList(Dispatchers.IO)
      .map { list -> list.map { it.asAyahHighlight() } }
      .map { it.toImmutableList() }
  }
}
