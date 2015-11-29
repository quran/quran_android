package com.quran.labs.androidquran.dao;

import java.util.List;

public class Bookmark {

  public long mId;
  public Integer mSura;
  public Integer mAyah;
  public int mPage;
  public long mTimestamp;
  public List<Tag> mTags;

  public Bookmark(long id, Integer sura, Integer ayah,
      int page, long timestamp) {
    mId = id;
    mSura = sura;
    mAyah = ayah;
    mPage = page;
    mTimestamp = timestamp;
  }

  public boolean isPageBookmark() {
    return mSura == null && mAyah == null;
  }
}
