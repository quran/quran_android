package com.quran.labs.androidquran.presenter.quran;

import android.graphics.RectF;

import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.dao.Bookmark;

import java.util.List;
import java.util.Map;

public interface QuranPageScreen {
  void setBookmarksOnPage(List<Bookmark> bookmarks);
  void setPageCoordinates(int page, RectF pageCoordinates);
  void setAyahCoordinatesError();
  void setAyahCoordinatesData(int page, Map<String, List<AyahBounds>> coordinates);
}
