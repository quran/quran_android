package com.quran.labs.androidquran.ui.translation;

import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.data.model.SuraAyah;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class TranslationViewRow {

  @IntDef({ Type.BASMALLAH, Type.SURA_HEADER, Type.QURAN_TEXT, Type.TRANSLATOR,
      Type.TRANSLATION_TEXT, Type.VERSE_NUMBER, Type.SPACER })
  @interface Type {
    int BASMALLAH = 0;
    int SURA_HEADER = 1;
    int QURAN_TEXT = 2;
    int TRANSLATOR = 3;
    int TRANSLATION_TEXT = 4;
    int VERSE_NUMBER = 5;
    int SPACER = 6;
  }

  @Type final int type;
  @NonNull final QuranAyahInfo ayahInfo;
  @Nullable final CharSequence data;
  final int translationIndex;
  @Nullable final SuraAyah link;
  final boolean isArabic;

  TranslationViewRow(int type, @NonNull QuranAyahInfo ayahInfo) {
    this(type, ayahInfo, null);
  }

  TranslationViewRow(int type, @NonNull QuranAyahInfo ayahInfo, @Nullable CharSequence data) {
    this(type, ayahInfo, data, -1, null, false);
  }

  TranslationViewRow(int type,
                     @NonNull QuranAyahInfo ayahInfo,
                     @Nullable CharSequence data,
                     int translationIndex,
                     @Nullable SuraAyah link,
                     boolean isArabic) {
    this.type = type;
    this.ayahInfo = ayahInfo;
    this.data = data;
    this.translationIndex = translationIndex;
    this.link = link;
    this.isArabic = isArabic;
  }
}
