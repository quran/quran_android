package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.quran.data.core.QuranInfo;
import com.quran.data.model.bookmark.Bookmark;
import com.quran.data.model.bookmark.Tag;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.QuranDisplayData;

import javax.inject.Inject;

public class QuranRowFactory {
  private final QuranInfo quranInfo;
  private final QuranDisplayData quranDisplayData;
  
  @Inject
  public QuranRowFactory(QuranInfo quranInfo, QuranDisplayData quranDisplayData) {
    this.quranInfo = quranInfo;
    this.quranDisplayData = quranDisplayData;
  }

  public QuranRow fromRecentPageHeader(Context context, int count) {
    return new QuranRow.Builder()
        .withText(context.getResources().getQuantityString(R.plurals.plural_recent_pages, count))
        .withType(QuranRow.HEADER)
        .build();
  }

  public QuranRow fromPageBookmarksHeader(Context context) {
    return new QuranRow.Builder()
        .withText(context.getString(R.string.menu_bookmarks_page))
        .withType(QuranRow.HEADER).build();
  }

  public QuranRow fromAyahBookmarksHeader(Context context) {
    return new QuranRow.Builder()
        .withText(context.getString(R.string.menu_bookmarks_ayah))
        .withType(QuranRow.HEADER).build();
  }

  public QuranRow fromCurrentPage(Context context, int page, long timeStamp) {
    return new QuranRow.Builder()
        .withText(quranDisplayData.getSuraNameString(context, page))
        .withMetadata(quranDisplayData.getPageSubtitle(context, page))
        .withSura(quranDisplayData.safelyGetSuraOnPage(page))
        .withPage(page)
        .withDate(timeStamp)
        .withImageResource(R.drawable.bookmark_currentpage).build();
  }

  public QuranRow fromBookmark(Context context, Bookmark bookmark) {
    return fromBookmark(context, bookmark, null);
  }

  public QuranRow fromBookmark(Context context, Bookmark bookmark, Long tagId) {
    final QuranRow.Builder builder = new QuranRow.Builder();

    if (bookmark.isPageBookmark()) {
      final int sura = quranInfo.getSuraNumberFromPage(bookmark.getPage());
      builder.withText(quranDisplayData.getSuraNameString(context, bookmark.getPage()))
          .withMetadata(quranDisplayData.getPageSubtitle(context, bookmark.getPage()))
          .withType(QuranRow.PAGE_BOOKMARK)
          .withBookmark(bookmark)
          .withDate(bookmark.getTimestamp())
          .withSura(sura)
          .withImageResource(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_favorite);
    } else {
      String ayahText = bookmark.getAyahText();

      final String title;
      final String metadata;
      if (ayahText == null) {
        title = quranDisplayData.getAyahString(bookmark.getSura(), bookmark.getAyah(), context);
        metadata = quranDisplayData.getPageSubtitle(context, bookmark.getPage());
      } else {
        title = ayahText + "...";
        metadata = quranDisplayData.getAyahMetadata(bookmark.getSura(), bookmark.getAyah(),
            bookmark.getPage(), context);
      }

      builder.withText(title)
          .withMetadata(metadata)
          .withType(QuranRow.AYAH_BOOKMARK)
          .withBookmark(bookmark)
          .withDate(bookmark.getTimestamp())
          .withImageResource(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_favorite)
          .withImageOverlayColor(ContextCompat.getColor(context, R.color.ayah_bookmark_color));
    }

    if (tagId != null) {
      builder.withTagId(tagId);
    }
    return builder.build();
  }

  public QuranRow fromTag(Tag tag) {
    return new QuranRow.Builder()
        .withType(QuranRow.BOOKMARK_HEADER)
        .withText(tag.getName())
        .withTagId(tag.getId())
        .build();
  }

  public static QuranRow fromNotTaggedHeader(Context context) {
    return new QuranRow.Builder()
        .withType(QuranRow.BOOKMARK_HEADER)
        .withText(context.getString(R.string.not_tagged))
        .build();
  }
}
