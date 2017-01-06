package com.quran.labs.androidquran.data;

public class VerseRange {
  public final int startSura;
  public final int startAyah;
  public final int endingSura;
  public final int endingAyah;
  public final int versesInRange;

  public VerseRange(int startSura, int startAyah, int endingSura, int endingAyah) {
    this.startSura = startSura;
    this.startAyah = startAyah;
    this.endingSura = endingSura;
    this.endingAyah = endingAyah;
    int delta = QuranInfo.getAyahId(endingSura, endingAyah) -
        QuranInfo.getAyahId(startSura, startAyah);
    // adding 1 because in the case of a single ayah, there is 1 ayah in that range, not 0
    versesInRange = 1 + (delta > 0 ? delta : -delta);
  }
}
