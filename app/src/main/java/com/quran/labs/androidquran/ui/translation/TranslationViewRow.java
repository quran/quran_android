package com.quran.labs.androidquran.ui.translation;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.QuranAyah;

class TranslationViewRow {

  @IntDef({ Type.BASMALLAH, Type.SURA_HEADER, Type.QURAN_TEXT,
      Type.TRANSLATION_TEXT, Type.VERSE_NUMBER, Type.SPACER })
  @interface Type {
    int BASMALLAH = 0;
    int SURA_HEADER = 1;
    int QURAN_TEXT = 2;
    int TRANSLATION_TEXT = 3;
    int VERSE_NUMBER = 4;
    int SPACER = 5;
  }

  @Type final int type;
  @NonNull final QuranAyah data;

  TranslationViewRow(int type, @NonNull QuranAyah data) {
    this.type = type;
    this.data = data;
  }
}
