package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.AyahReadingBookmark
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.R
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
      .withText(context.getString(R.string.menu_bookmarks_ayah))
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
  fun fromBookmark(context: Context, bookmark: Bookmark, tagId: String? = null): QuranRow {
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
        .withImageResource(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_favorite)
        .withImageOverlayColorResource(R.color.ayah_bookmark_color)
    }

    if (tagId != null) {
      builder.withTagId(tagId)
    }
    return builder.build()
  }

  fun fromTag(tag: Tag): QuranRow {
    return QuranRow.Builder()
      .withType(QuranRow.BOOKMARK_HEADER)
      .withText(tag.name)
      .withTagId(tag.id)
      .build()
  }

  fun fromNotTaggedHeader(context: Context): QuranRow {
    return QuranRow.Builder()
      .withType(QuranRow.BOOKMARK_HEADER)
      .withText(context.getString(R.string.not_tagged))
      .build()
  }
}
