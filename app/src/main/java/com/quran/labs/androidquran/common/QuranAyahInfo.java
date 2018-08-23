package com.quran.labs.androidquran.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * QuranAyahInfo
 * TODO: This should become QuranAyah at some point in the future
 * This is because most usages of QuranAyah should actually be usages of SuraAyah
 */
public class QuranAyahInfo {
  public final int sura;
  public final int ayah;
  public final int ayahId;
  @Nullable public final String arabicText;
  @NonNull public final List<String> texts;

  public QuranAyahInfo(int sura,
                       int ayah,
                       @Nullable String arabicText,
                       @NonNull List<String> texts,
                       int ayahId) {
    this.sura = sura;
    this.ayah = ayah;
    this.arabicText = arabicText;
    this.texts = Collections.unmodifiableList(texts);
    this.ayahId = ayahId;
  }
}
