package com.quran.labs.androidquran.ui.helpers;

import java.util.Set;

public interface AyahTracker {
  public void highlightAyah(int sura, int ayah, HighlightType type);
  public void highlightAyat(
      int page, Set<String> ayahKeys, HighlightType type);
  public void unHighlightAyah(int sura, int ayah, HighlightType type);
  public void unHighlightAyahs(HighlightType type);
  public float[] getToolBarPosition(int sura, int ayah,
      int toolBarWidth, int toolBarHeight);
}