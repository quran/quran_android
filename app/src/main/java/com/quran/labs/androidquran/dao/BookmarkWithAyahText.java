package com.quran.labs.androidquran.dao;

public class BookmarkWithAyahText extends Bookmark {
  public final String ayahText;

  public BookmarkWithAyahText(Bookmark bookmark, String ayahText) {
    super(bookmark.id, bookmark.sura, bookmark.ayah,
        bookmark.page, bookmark.timestamp, bookmark.tags);
    this.ayahText = ayahText;
  }

  @Override
  public String getAyahText() {
    return this.ayahText;
  }
}
