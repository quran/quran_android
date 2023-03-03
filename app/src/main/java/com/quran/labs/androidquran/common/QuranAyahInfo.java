package com.quran.labs.androidquran.common;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.quran.data.model.SuraAyah;

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
  @NonNull public final List<TranslationMetadata> texts;

  public QuranAyahInfo(int sura,
                       int ayah,
                       @Nullable String arabicText,
                       @NonNull List<TranslationMetadata> texts,
                       int ayahId) {
    this.sura = sura;
    this.ayah = ayah;
    this.arabicText = arabicText;
    this.texts = Collections.unmodifiableList(texts);
    this.ayahId = ayahId;
  }

  public SuraAyah asSuraAyah() {
    return new SuraAyah(this.sura, this.ayah);
  }
}
