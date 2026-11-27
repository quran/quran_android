package com.quran.labs.androidquran.extra.feature.linebyline.model

import android.graphics.Bitmap
import com.quran.data.model.PageAppearance
import com.quran.mobile.linebyline.data.dao.AyahMarkerInfo
import com.quran.mobile.linebyline.data.dao.SuraHeader
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class PageInfo(
  val page: Int,
  val pageType: String,
  val displayText: DisplayText,
  val displaySettings: DisplaySettings,
  val compositedPageBitmap: Bitmap? = null,
  val compositedWidth: Int = 0,
  val compositedHeight: Int = 0,
  val compositedIsNightMode: Boolean = false,
  val compositedTextBrightness: Int = 255,
  val compositedShowDividers: Boolean = false,
  val compositedLineColor: Int = 0,
  val compositedHeaderColor: Int = 0,
  val compositedAyahColor: Int = 0,
  val lineModels: ImmutableList<LineModel> = persistentListOf(),
  val suraHeaders: ImmutableList<SuraHeader> = persistentListOf(),
  val ayahMarkers: ImmutableList<AyahMarkerInfo> = persistentListOf(),
  val ayahHighlights: ImmutableList<HighlightAyah> = persistentListOf(),
  val readingBookmarks: ImmutableList<ReadingBookmarkAyah> = persistentListOf(),
  val sidelines: ImmutableList<SidelineModel> = persistentListOf(),
  val targetScrollPosition: Int = -1,
  val showSidelines: Boolean = false,
  val skippedPageCount: Int = 0
)

val EmptySettings =
  DisplaySettings(
    PageAppearance.Light,
    255,
    0,
    null,
    showHeaderFooter = false,
    showSidelines = false,
    showLineDividers = false
  )

val EmptyPageInfo =
  PageInfo(
    page = 0,
    pageType = "",
    displayText = DisplayText("", "", "", "", ""),
    displaySettings = EmptySettings
  )
