package com.quran.labs.androidquran.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Bookmark {

  public final long id;
  public final Integer sura;
  public final Integer ayah;
  public final int page;
  public final long timestamp;
  public final List<Long> tags;

  public Bookmark(long id, Integer sura, Integer ayah, int page) {
    this(id, sura, ayah, page, System.currentTimeMillis());
  }

  public Bookmark(long id, Integer sura, Integer ayah, int page, long timestamp) {
    this(id, sura, ayah, page, timestamp, Collections.<Long>emptyList());
  }

  public Bookmark(long id, Integer sura, Integer ayah, int page, long timestamp, List<Long> tags) {
    this.id = id;
    this.sura = sura;
    this.ayah = ayah;
    this.page = page;
    this.timestamp = timestamp;
    this.tags = Collections.unmodifiableList(tags);
  }

  public boolean isPageBookmark() {
    return sura == null && ayah == null;
  }

  public Bookmark withTags(List<Long> tagIds) {
    return new Bookmark(id, sura, ayah, page, timestamp, new ArrayList<>(tagIds));
  }

  public String getAyahText() {
    return null;
  }

  public Bookmark withAyahText(String ayahText) {
    return new BookmarkWithAyahText(this, ayahText);
  }
}
