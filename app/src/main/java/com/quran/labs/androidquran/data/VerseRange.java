package com.quran.labs.androidquran.data;

public class VerseRange {
  public final int startSura;
  public final int startAyah;
  public final int endingSura;
  public final int endingAyah;

  public VerseRange(int startSura, int startAyah, int endingSura, int endingAyah) {
    this.startSura = startSura;
    this.startAyah = startAyah;
    this.endingSura = endingSura;
    this.endingAyah = endingAyah;
  }
}
