package com.quran.labs.androidquran.ui.helpers;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import com.quran.data.model.bookmark.Bookmark;
import com.quran.data.model.highlight.HighlightColor;

public class QuranRow {

  // Row Types
  public static final int NONE = 0;
  public static final int HEADER = 1;
  public static final int PAGE_BOOKMARK = 2;
  public static final int AYAH_BOOKMARK = 3;
  public static final int BOOKMARK_HEADER = 4;
  public static final int PAGE_READING_BOOKMARK = 5;
  public static final int AYAH_READING_BOOKMARK = 6;
  public static final int HIGHLIGHT_COLOR = 7;
  public static final int HIGHLIGHTED_AYAH = 8;

  public int sura;
  public int ayah;
  public int page;
  public String text;
  public String metadata;
  public int rowType;
  public Integer imageResource;
  public Integer imageFilterColorResource;
  public Integer juzType;
  public String juzOverlayText;
  public long dateAddedInMillis;

  // For Bookmarks
  public String tagId;
  public String bookmarkId;
  public Bookmark bookmark;

  public Integer itemCount;
  public boolean isCollapsible;
  public boolean isCollapsed;

  public HighlightColor highlightColor;

  public static class Builder {
    private String text;
    private String metadata;
    private int sura;
    private int ayah;
    private int page;
    private int rowType = NONE;
    private Integer imageResource;
    private Integer juzType;
    private String tagId;
    private String bookmarkId;
    private String juzOverlayText;
    private long dateAddedInMillis;
    private Integer imageFilterColorResource;
    private Bookmark bookmark;
    private Integer itemCount;
    private boolean isCollapsible;
    private boolean isCollapsed;
    private HighlightColor highlightColor;

    public Builder withType(int type) {
      rowType = type;
      return this;
    }

    public Builder withText(String text) {
      this.text = text;
      return this;
    }

    public Builder withMetadata(String metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder withBookmark(Bookmark bookmark) {
      if (!bookmark.isPageBookmark()) {
        sura = bookmark.getSura();
        ayah = bookmark.getAyah();
      }
      page = bookmark.getPage();
      this.bookmark = bookmark;
      bookmarkId = bookmark.getId();
      return this;
    }

    public Builder withSura(int sura) {
      this.sura = sura;
      return this;
    }

    /**
     * Sets the ayah number for rows that navigate to a specific ayah without owning a Bookmark.
     */
    public Builder withAyah(int ayah) {
      this.ayah = ayah;
      return this;
    }

    public Builder withPage(int page) {
      this.page = page;
      return this;
    }

    public Builder withImageResource(@DrawableRes int resId) {
      imageResource = resId;
      return this;
    }

    public Builder withImageOverlayColorResource(@ColorRes int colorResource) {
      imageFilterColorResource = colorResource;
      return this;
    }

    public Builder withJuzType(int juzType) {
      this.juzType = juzType;
      return this;
    }

    public Builder withJuzOverlayText(String text) {
      juzOverlayText = text;
      return this;
    }

    public Builder withTagId(String id) {
      tagId = id;
      return this;
    }

    public Builder withDate(long timeStamp) {
      dateAddedInMillis = timeStamp * 1000;
      return this;
    }

    public Builder withItemCount(int itemCount) {
      this.itemCount = itemCount;
      return this;
    }

    public Builder withCollapsedState(boolean isCollapsed) {
      this.isCollapsible = true;
      this.isCollapsed = isCollapsed;
      return this;
    }

    public Builder withHighlightColor(HighlightColor highlightColor) {
      this.highlightColor = highlightColor;
      return this;
    }

    public QuranRow build() {
      return new QuranRow(text, metadata, rowType, sura,
          ayah, page, imageResource, imageFilterColorResource, juzType,
          juzOverlayText, bookmarkId, tagId, bookmark, dateAddedInMillis, itemCount,
          isCollapsible, isCollapsed, highlightColor);
    }
  }

  private QuranRow(String text, String metadata, int rowType,
      int sura, int ayah, int page, Integer imageResource, Integer filterColorResource,
      Integer juzType, String juzOverlayText, String bookmarkId, String tagId, Bookmark bookmark,
                   long dateAddedInMillis, Integer itemCount, boolean isCollapsible,
                   boolean isCollapsed, HighlightColor highlightColor) {
    this.text = text;
    this.rowType = rowType;
    this.sura = sura;
    this.ayah = ayah;
    this.page = page;
    this.metadata = metadata;
    this.imageResource = imageResource;
    this.imageFilterColorResource = filterColorResource;
    this.juzType = juzType;
    this.juzOverlayText = juzOverlayText;
    this.tagId = tagId;
    this.bookmarkId = bookmarkId;
    this.bookmark = bookmark;
    this.dateAddedInMillis = dateAddedInMillis;
    this.itemCount = itemCount;
    this.isCollapsible = isCollapsible;
    this.isCollapsed = isCollapsed;
    this.highlightColor = highlightColor;
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

  public boolean isReadingBookmark() {
    return rowType == PAGE_READING_BOOKMARK || rowType == AYAH_READING_BOOKMARK;
  }

  public boolean isAyahBookmark() {
    return rowType == AYAH_BOOKMARK || rowType == AYAH_READING_BOOKMARK;
  }

  public boolean isHighlightColor() {
    return rowType == HIGHLIGHT_COLOR;
  }

  public boolean isHighlightedAyah() {
    return rowType == HIGHLIGHTED_AYAH;
  }
}
