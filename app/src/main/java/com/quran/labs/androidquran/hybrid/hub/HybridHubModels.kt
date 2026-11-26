package com.quran.labs.androidquran.hybrid.hub

import androidx.compose.runtime.Immutable
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.hybrid.contract.DailyWirdState
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab

enum class HubIndexSegment {
  SURAS,
  JUZ,
  HIZB
}

@Immutable
data class SuraIndexItem(
  val sura: Int,
  val name: String,
  val metaText: String,
  val startPage: Int,
  val juz: Int,
  val isMakki: Boolean,
  val ayahCount: Int
)

@Immutable
data class JuzIndexItem(
  val juz: Int,
  val title: String,
  val startSuraName: String,
  val startAyah: Int,
  val startPage: Int,
  val previewText: String
)

@Immutable
data class HizbIndexItem(
  val hizb: Int,
  val juz: Int,
  val startSuraName: String,
  val startAyah: Int,
  val startPage: Int,
  val previewText: String
)

@Immutable
data class HubReadingBookmarkItem(
  val slot: ReadingBookmarkType,
  val page: Int,
  val sura: Int?,
  val ayah: Int?,
  val suraName: String,
  val juz: Int,
  val customName: String? = null
)

@Immutable
data class HubRecentPageItem(
  val page: Int,
  val suraName: String,
  val juz: Int
)

@Immutable
data class HubAyahBookmarkItem(
  val id: String,
  val sura: Int,
  val ayah: Int,
  val page: Int,
  val suraName: String,
  val juz: Int,
  val ayahText: String?
)

@Immutable
data class HubHighlightItem(
  val sura: Int,
  val ayah: Int,
  val page: Int,
  val suraName: String,
  val juz: Int,
  val ayahText: String?,
  val color: HighlightColor? = null
)

@Immutable
data class HubBookmarksUiState(
  val readingBookmarks: List<HubReadingBookmarkItem> = emptyList(),
  val recentPages: List<HubRecentPageItem> = emptyList(),
  val ayahBookmarks: List<HubAyahBookmarkItem> = emptyList(),
  val highlights: List<HubHighlightItem> = emptyList(),
  val isLoaded: Boolean = false
) {
  val isEmpty: Boolean
    get() = readingBookmarks.isEmpty() &&
      recentPages.isEmpty() &&
      ayahBookmarks.isEmpty() &&
      highlights.isEmpty()
}

@Immutable
data class HybridHubUiState(
  val selectedTab: HybridNavTab = HybridNavTab.INDEX,
  val indexSegment: HubIndexSegment = HubIndexSegment.SURAS,
  val themeStyle: AppThemeStyle = AppThemeStyle.DEFAULT,
  val appTheme: String = Constants.THEME_DEFAULT,
  val dailyWird: DailyWirdState = DailyWirdState(),
  val fallbackLastSuraName: String = "",
  val suras: List<SuraIndexItem> = emptyList(),
  val juzs: List<JuzIndexItem> = emptyList(),
  val hizbs: List<HizbIndexItem> = emptyList(),
  val bookmarks: HubBookmarksUiState = HubBookmarksUiState()
)
