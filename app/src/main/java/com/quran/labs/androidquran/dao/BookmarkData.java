package com.quran.labs.androidquran.dao;

import java.util.ArrayList;
import java.util.List;

public class BookmarkData {
  private final List<Tag> tags;
  private final List<Bookmark> bookmarks;
  private final List<RecentPage> recentPages;

  public BookmarkData(List<Tag> tags, List<Bookmark> bookmarks, List<RecentPage> recentPages) {
    this.tags = tags == null ? new ArrayList<>() : tags;
    this.bookmarks = bookmarks == null ? new ArrayList<>() : bookmarks;
    this.recentPages = recentPages == null ? new ArrayList<>() : recentPages;
  }

  public List<Tag> getTags() {
    return this.tags;
  }

  public List<Bookmark> getBookmarks() {
    return this.bookmarks;
  }

  public List<RecentPage> getRecentPages() {
    return this.recentPages;
  }
}
