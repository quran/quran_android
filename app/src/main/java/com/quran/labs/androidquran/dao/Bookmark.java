package com.quran.labs.androidquran.dao;

import java.util.List;

public class Bookmark {

  public long id;
  public Integer sura;
  public Integer ayah;
  public int page;
  public long timestamp;
  public List<Long> tags;

  public Bookmark(long id, Integer sura, Integer ayah, int page, long timestamp) {
    this.id = id;
    this.sura = sura;
    this.ayah = ayah;
    this.page = page;
    this.timestamp = timestamp;
  }

  public boolean isPageBookmark() {
    return sura == null && ayah == null;
  }
}
