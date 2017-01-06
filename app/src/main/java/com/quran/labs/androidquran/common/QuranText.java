package com.quran.labs.androidquran.common;

import android.support.annotation.NonNull;

public class QuranText {
  public final int sura;
  public final int ayah;
  @NonNull public final String text;

  public QuranText(int sura, int ayah, @NonNull String text) {
    this.sura = sura;
    this.ayah = ayah;
    this.text = text;
  }
}
