package com.quran.labs.androidquran.ui.helpers;

import com.quran.labs.androidquran.widgets.HighlightingImageView;

public interface AyahTracker {
  public HighlightingImageView getHighlightingImageView(int page);
  public void highlightAyah(int sura, int ayah, HighlightType type);
  public void unHighlightAyah(int sura, int ayah, HighlightType type);
  public void unHighlightAyahs(HighlightType type);
}
