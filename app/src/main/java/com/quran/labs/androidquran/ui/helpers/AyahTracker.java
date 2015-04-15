package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.widgets.AyahToolBar;

import android.graphics.drawable.BitmapDrawable;

import java.util.Set;

public interface AyahTracker {
  void highlightAyah(int sura, int ayah, HighlightType type);
  void highlightAyah(int sura, int ayah,
      HighlightType type, boolean scrollToAyah);
  void highlightAyat(
      int page, Set<String> ayahKeys, HighlightType type);
  void unHighlightAyah(int sura, int ayah, HighlightType type);
  void unHighlightAyahs(HighlightType type);
  AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
      int toolBarWidth, int toolBarHeight);
  void updateView();
  void onLoadImageResponse(BitmapDrawable drawable, Response response);
}