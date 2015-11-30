package com.quran.labs.androidquran.dao;

import java.util.List;

public class BookmarkData {
  private List<Tag> tags;
  private List<Bookmark> bookmarks;

  public BookmarkData(List<Tag> tags, List<Bookmark> bookmarks) {
    this.tags = tags;
    this.bookmarks = bookmarks;
  }

  public List<Tag> getTags() {
    return this.tags;
  }

  public List<Bookmark> getBookmarks() {
    return this.bookmarks;
  }
}
