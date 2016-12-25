package com.quran.labs.androidquran.common;

import com.quran.labs.androidquran.ui.helpers.HighlightType;

public class HighlightInfo {
  public final int sura;
  public final int ayah;
  public final HighlightType highlightType;
  public final boolean scrollToAyah;

  public HighlightInfo(int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    this.sura = sura;
    this.ayah = ayah;
    this.highlightType = type;
    this.scrollToAyah = scrollToAyah;
  }
}
