package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.dao.Bookmark;

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
  public Bookmark bookmark;

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
    private Bookmark mBookmark;

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

    public Builder withBookmark(Bookmark bookmark) {
      if (!bookmark.isPageBookmark()) {
        mSura = bookmark.sura;
        mAyah = bookmark.ayah;
      }
      mPage = bookmark.page;
      mBookmark = bookmark;
      mBookmarkId = bookmark.id;
      return this;
    }

    public Builder withSura(int sura) {
      mSura = sura;
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

    public Builder withTagId(long id) {
      mTagId = id;
      return this;
    }

    public QuranRow build() {
      return new QuranRow(mText, mMetadata, mRowType, mSura,
          mAyah, mPage, mImageResource, mImageFilterColor, mJuzType,
          mJuzOverlayText, mBookmarkId, mTagId, mBookmark);
    }
  }

  private QuranRow(String text, String metadata, int rowType,
      int sura, int ayah, int page, Integer imageResource, Integer filterColor,
      Integer juzType, String juzOverlayText, long bookmarkId, long tagId, Bookmark bookmark) {
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
    this.bookmark = bookmark;
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