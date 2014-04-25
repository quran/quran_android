package com.quran.labs.androidquran.ui.helpers;

public interface AyahTracker {
  public void highlightAyah(int sura, int ayah, HighlightType type);
  public void unHighlightAyah(int sura, int ayah, HighlightType type);
  public void unHighlightAyahs(HighlightType type);
}
