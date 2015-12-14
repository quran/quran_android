package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.data.QuranInfo;

import android.content.Context;
import android.support.v4.content.ContextCompat;

public class QuranRow {

  // Row Types
  public static final int NONE = 0;
  public static final int HEADER = 1;
  public static final int PAGE_BOOKMARK = 2;
  public static final int AYAH_BOOKMARK = 3;
  public static final int BOOKMARK_HEADER = 4;

  public int sura;
  public int ayah;
  public int page;
  public String text;
  public String metadata;
  public int rowType;
  public Integer imageResource;
  public Integer imageFilterColor;
  public Integer juzType;
  public String juzOverlayText;

  // For Bookmarks
  public long tagId;
  public long bookmarkId;

  public static class Builder {

    private String mText;
    private String mMetadata;
    private int mSura;
    private int mAyah;
    private int mPage;
    private int mRowType = NONE;
    private Integer mImageResource;
    private Integer mJuzType;
    private long mTagId = -1;
    private long mBookmarkId = -1;
    private String mJuzOverlayText;
    private Integer mImageFilterColor;

    public Builder withType(int type) {
      mRowType = type;
      return this;
    }

    public Builder withText(String text) {
      mText = text;
      return this;
    }

    public Builder withMetadata(String metadata) {
      mMetadata = metadata;
      return this;
    }

    public Builder withSura(int sura) {
      mSura = sura;
      return this;
    }

    public Builder withAyah(int ayah) {
      mAyah = ayah;
      return this;
    }

    public Builder withPage(int page) {
      mPage = page;
      return this;
    }

    public Builder withImageResource(int resId) {
      mImageResource = resId;
      return this;
    }

    public Builder withImageOverlayColor(int color) {
      mImageFilterColor = color;
      return this;
    }

    public Builder withJuzType(int juzType) {
      mJuzType = juzType;
      return this;
    }

    public Builder withJuzOverlayText(String text) {
      mJuzOverlayText = text;
      return this;
    }

    public Builder withBookmarkId(long id) {
      mBookmarkId = id;
      return this;
    }

    public Builder withTagId(long id) {
      mTagId = id;
      return this;
    }

    public QuranRow build() {
      return new QuranRow(mText, mMetadata, mRowType, mSura,
          mAyah, mPage, mImageResource, mImageFilterColor, mJuzType,
          mJuzOverlayText, mBookmarkId, mTagId);
    }
  }

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
          .withSura(sura)
          .withImageResource(R.drawable.ic_favorite);
    } else {
      final String title =
          QuranInfo.getAyahString(bookmark.sura, bookmark.ayah, context);
      builder.withText(title)
          .withMetadata(QuranInfo.getPageSubtitle(context, bookmark.page))
          .withType(QuranRow.AYAH_BOOKMARK)
          .withSura(bookmark.sura)
          .withAyah(bookmark.ayah)
          .withImageResource(R.drawable.ic_favorite)
          .withImageOverlayColor(ContextCompat.getColor(context, R.color.ayah_bookmark_color));
    }
    builder.withPage(bookmark.page)
        .withBookmarkId(bookmark.id);
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

  private QuranRow(String text, String metadata, int rowType,
      int sura, int ayah, int page, Integer imageResource, Integer filterColor,
      Integer juzType, String juzOverlayText, long bookmarkId, long tagId) {
    this.text = text;
    this.rowType = rowType;
    this.sura = sura;
    this.ayah = ayah;
    this.page = page;
    this.metadata = metadata;
    this.imageResource = imageResource;
    this.imageFilterColor = filterColor;
    this.juzType = juzType;
    this.juzOverlayText = juzOverlayText;
    this.tagId = tagId;
    this.bookmarkId = bookmarkId;
  }

  public boolean isHeader() {
    return rowType == HEADER || rowType == BOOKMARK_HEADER;
  }

  public boolean isBookmarkHeader() {
    return rowType == BOOKMARK_HEADER;
  }

  public boolean isBookmark() {
    return rowType == PAGE_BOOKMARK || rowType == AYAH_BOOKMARK;
  }

  public boolean isAyahBookmark() {
    return rowType == AYAH_BOOKMARK;
  }
}