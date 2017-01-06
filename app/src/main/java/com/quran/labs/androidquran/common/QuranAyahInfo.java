package com.quran.labs.androidquran.common;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.quran.labs.androidquran.data.QuranInfo;

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
                       @NonNull List<String> texts) {
    this.sura = sura;
    this.ayah = ayah;
    this.arabicText = arabicText;
    this.texts = Collections.unmodifiableList(texts);
    this.ayahId = QuranInfo.getAyahId(sura, ayah);
  }
}
