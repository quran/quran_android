package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.data.QuranInfo;

import android.content.Context;
import android.support.v4.content.ContextCompat;

public class QuranRowFactory {

  public static QuranRow fromCurrentPageHeader(Context context) {
    return new QuranRow.Builder()
        .withText(context.getString(R.string.bookmarks_current_page))
        .withType(QuranRow.HEADER).build();
  }

  public static QuranRow fromPageBookmarksHeader(Context context) {
    return new QuranRow.Builder()
        .withText(context.getString(R.string.menu_bookmarks_page))
        .withType(QuranRow.HEADER).build();
  }

  public static QuranRow fromAyahBookmarksHeader(Context context) {
    return new QuranRow.Builder()
        .withText(context.getString(R.string.menu_bookmarks_ayah))
        .withType(QuranRow.HEADER).build();
  }

  public static QuranRow fromCurrentPage(Context context, int page) {
    return new QuranRow.Builder()
        .withText(QuranInfo.getSuraNameString(context, page))
        .withMetadata(QuranInfo.getPageSubtitle(context, page))
        .withSura(QuranInfo.PAGE_SURA_START[page-1])
        .withPage(page)
        .withImageResource(R.drawable.bookmark_currentpage).build();
  }

  public static QuranRow fromBookmark(Context context, Bookmark bookmark) {
    return fromBookmark(context, bookmark, null);
  }

  public static QuranRow fromBookmark(Context context, Bookmark bookmark, Long tagId) {
    final QuranRow.Builder builder = new QuranRow.Builder();

    if (bookmark.isPageBookmark()) {
      final int sura = QuranInfo.getSuraNumberFromPage(bookmark.page);
      builder.withText(QuranInfo.getSuraNameString(context, bookmark.page))
          .withMetadata(QuranInfo.getPageSubtitle(context, bookmark.page))
          .withType(QuranRow.PAGE_BOOKMARK)
          .withBookmark(bookmark)
          .withSura(sura)
          .withImageResource(R.drawable.ic_favorite);
    } else {
      String ayahText = bookmark.getAyahText();

      final String title;
      final String metadata;
      if (ayahText == null) {
        title = QuranInfo.getAyahString(bookmark.sura, bookmark.ayah, context);
        metadata = QuranInfo.getPageSubtitle(context, bookmark.page);
      } else {
        title = ayahText;
        metadata = QuranInfo.getAyahMetadata(bookmark.sura, bookmark.ayah, bookmark.page, context);
      }

      builder.withText(title)
          .withMetadata(metadata)
          .withType(QuranRow.AYAH_BOOKMARK)
          .withBookmark(bookmark)
          .withImageResource(R.drawable.ic_favorite)
          .withImageOverlayColor(ContextCompat.getColor(context, R.color.ayah_bookmark_color));
    }

    if (tagId != null) {
      builder.withTagId(tagId);
    }
    return builder.build();
  }

  public static QuranRow fromTag(Tag tag) {
    return new QuranRow.Builder()
        .withType(QuranRow.BOOKMARK_HEADER)
        .withText(tag.name)
        .withTagId(tag.id)
        .build();
  }

  public static QuranRow fromNotTaggedHeader(Context context) {
    return new QuranRow.Builder()
        .withType(QuranRow.BOOKMARK_HEADER)
        .withText(context.getString(R.string.not_tagged))
        .build();
  }
}
