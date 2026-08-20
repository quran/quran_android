package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.Tag
import com.quran.data.model.highlight.Highlight
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.dao.bookmark.AyahMark
import com.quran.labs.androidquran.common.ui.core.CollectionNames
import com.quran.labs.androidquran.common.ui.core.HighlightColors
import com.quran.labs.androidquran.data.QuranDisplayData
import dev.zacsweers.metro.Inject

class QuranRowFactory @Inject constructor(
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData
) {
  fun fromRecentPageHeader(context: Context, count: Int): QuranRow {
    return QuranRow.Builder()
      .withText(
        context.getResources().getQuantityString(R.plurals.plural_recent_pages, count)
      )
      .withType(QuranRow.HEADER)
      .build()
  }

  fun fromPageBookmarksHeader(context: Context): QuranRow {
    return QuranRow.Builder()
      .withText(context.getString(R.string.menu_bookmarks_page))
      .withType(QuranRow.HEADER).build()
  }

  fun fromAyahBookmarksHeader(context: Context): QuranRow {
    return QuranRow.Builder()
      .withText(context.getString(R.string.ayahs))
      .withType(QuranRow.HEADER).build()
  }

  fun fromReadingBookmarkHeader(context: Context): QuranRow {
    return QuranRow.Builder()
      .withText(context.getString(R.string.reading_bookmark))
      .withType(QuranRow.HEADER)
      .build()
  }

  fun fromCurrentPage(context: Context, page: Int, timeStamp: Long): QuranRow {
    return QuranRow.Builder()
      .withText(quranDisplayData.getSuraNameString(context, page))
      .withMetadata(quranDisplayData.getPageSubtitle(context, page))
      .withSura(quranDisplayData.safelyGetSuraOnPage(page))
      .withPage(page)
      .withDate(timeStamp)
      .withImageResource(R.drawable.bookmark_currentpage)
      .withImageOverlayColorResource(R.color.icon_tint)
      .build()
  }

  fun fromReadingBookmark(context: Context, readingBookmark: ReadingBookmark): QuranRow {
    return when (readingBookmark) {
      is PageReadingBookmark -> {
        val page = readingBookmark.page.takeIf(quranInfo::isValidPage) ?: 1
        QuranRow.Builder()
          .withText(quranDisplayData.getSuraNameString(context, page))
          .withMetadata(quranDisplayData.getPageSubtitle(context, page))
          .withType(QuranRow.PAGE_READING_BOOKMARK)
          .withSura(quranDisplayData.safelyGetSuraOnPage(page))
          .withPage(page)
          .withDate(readingBookmark.timestamp)
          .withImageResource(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_favorite)
          .withImageOverlayColorResource(R.color.icon_tint)
          .build()
      }

      is AyahReadingBookmark -> {
        val page = quranInfo.getPageFromSuraAyah(readingBookmark.sura, readingBookmark.ayah)
          .takeIf(quranInfo::isValidPage)
          ?: 1
        QuranRow.Builder()
          .withText(
            quranDisplayData.getAyahString(
              readingBookmark.sura,
              readingBookmark.ayah,
              context
            )
          )
          .withMetadata(
            quranDisplayData.getAyahMetadata(
              readingBookmark.sura,
              readingBookmark.ayah,
              page,
              context
            )
          )
          .withType(QuranRow.AYAH_READING_BOOKMARK)
          .withSura(readingBookmark.sura)
          .withAyah(readingBookmark.ayah)
          .withPage(page)
          .withDate(readingBookmark.timestamp)
          .withImageResource(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_favorite)
          .withImageOverlayColorResource(R.color.ayah_bookmark_color)
          .build()
      }
    }
  }

  @JvmOverloads
  fun fromBookmark(
    context: Context,
    bookmark: Bookmark,
    tagId: String? = null,
    mark: AyahMark = AyahMark.Bookmark
  ): QuranRow {
    val builder = QuranRow.Builder()

    if (bookmark.isPageBookmark()) {
      val sura = quranInfo.getSuraNumberFromPage(bookmark.page)
      builder.withText(quranDisplayData.getSuraNameString(context, bookmark.page))
        .withMetadata(quranDisplayData.getPageSubtitle(context, bookmark.page))
        .withType(QuranRow.PAGE_BOOKMARK)
        .withBookmark(bookmark)
        .withDate(bookmark.timestamp)
        .withSura(sura)
        .withImageResource(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_favorite)
        .withImageOverlayColorResource(R.color.icon_tint)
    } else {
      val ayahText = bookmark.ayahText

      val title: String
      val metadata: String
      if (ayahText == null) {
        title = quranDisplayData.getAyahString(bookmark.sura!!, bookmark.ayah!!, context)
        metadata = quranDisplayData.getPageSubtitle(context, bookmark.page)
      } else {
        title = "$ayahText..."
        metadata = quranDisplayData.getAyahMetadata(
          bookmark.sura!!, bookmark.ayah!!,
          bookmark.page, context
        )
      }

      builder.withText(title)
        .withMetadata(metadata)
        .withType(QuranRow.AYAH_BOOKMARK)
        .withBookmark(bookmark)
        .withDate(bookmark.timestamp)
        .withImageResource(ribbonFor(mark))
        .withImageOverlayColorResource(ribbonColorFor(mark))
    }

    if (tagId != null) {
      builder.withTagId(tagId)
    }
    return builder.build()
  }

  fun fromTag(context: Context, tag: Tag, count: Int, isCollapsed: Boolean): QuranRow {
    return QuranRow.Builder()
      .withType(QuranRow.BOOKMARK_HEADER)
      .withText(CollectionNames.displayName(context, tag))
      .withTagId(tag.id)
      .withItemCount(count)
      .withCollapsedState(isCollapsed)
      .withSystemCollection(tag.isSystem)
      .build()
  }

  fun fromHighlightsHeader(context: Context): QuranRow {
    return QuranRow.Builder()
      .withText(context.getString(R.string.highlights))
      .withType(QuranRow.HEADER)
      .build()
  }

  fun fromHighlightColor(context: Context, color: HighlightColor, count: Int): QuranRow {
    return QuranRow.Builder()
      .withType(QuranRow.HIGHLIGHT_COLOR)
      .withText(context.getString(HighlightColors[color].nameResourceId))
      .withHighlightColor(color)
      .withImageOverlayColorResource(listColorFor(color))
      .withItemCount(count)
      .build()
  }

  fun fromSuraHeader(context: Context, sura: Int): QuranRow {
    return QuranRow.Builder()
      .withType(QuranRow.HEADER)
      .withText(quranDisplayData.getSuraName(context, sura, true))
      .withSura(sura)
      .withItemCount(sura)
      .build()
  }

  @JvmOverloads
  fun fromHighlight(
    context: Context,
    highlight: Highlight,
    ayahText: String? = null
  ): QuranRow {
    val sura = highlight.suraAyah.sura
    val ayah = highlight.suraAyah.ayah
    val page = quranInfo.getPageFromSuraAyah(sura, ayah).takeIf(quranInfo::isValidPage) ?: 1

    // without the arabic database there is no ayah text to show, so the row falls back to naming
    // the ayah in its title and to the page subtitle underneath, the way bookmark rows do
    val title: String
    val metadata: String
    if (ayahText == null) {
      title = quranDisplayData.getAyahString(sura, ayah, context)
      metadata = quranDisplayData.getPageSubtitle(context, page)
    } else {
      title = "$ayahText..."
      metadata = quranDisplayData.getAyahMetadata(sura, ayah, page, context)
    }

    return QuranRow.Builder()
      .withType(QuranRow.HIGHLIGHTED_AYAH)
      .withText(title)
      .withMetadata(metadata)
      .withSura(sura)
      .withAyah(ayah)
      .withPage(page)
      .withDate(highlight.timestamp.epochSeconds)
      .withHighlightColor(highlight.color)
      .withImageResource(ribbonFor(AyahMark.Highlighted(highlight.color)))
      .withImageOverlayColorResource(ribbonColorFor(AyahMark.Highlighted(highlight.color)))
      .build()
  }

  @DrawableRes
  private fun ribbonFor(mark: AyahMark): Int {
    return when (mark) {
      AyahMark.Unhighlighted -> R.drawable.ic_bookmark_outline_24
      else -> R.drawable.ic_bookmark_filled_24
    }
  }

  @ColorRes
  private fun ribbonColorFor(mark: AyahMark): Int {
    return when (mark) {
      AyahMark.Bookmark -> R.color.ayah_bookmark_color
      AyahMark.Unhighlighted -> R.color.unhighlighted_bookmark_color
      is AyahMark.Highlighted -> listColorFor(mark.color)
    }
  }

  @ColorRes
  private fun listColorFor(highlightColor: HighlightColor): Int {
    return when (highlightColor) {
      HighlightColor.YELLOW -> R.color.highlight_list_yellow
      HighlightColor.GREEN -> R.color.highlight_list_green
      HighlightColor.BLUE -> R.color.highlight_list_blue
      HighlightColor.RED -> R.color.highlight_list_red
      HighlightColor.PURPLE -> R.color.highlight_list_purple
    }
  }
}
