package com.quran.labs.androidquran.ui.translation;

import com.quran.labs.androidquran.common.QuranAyahInfo;

public interface OnTranslationActionListener {
  void onTranslationAction(QuranAyahInfo ayah, String[] translationNames, int actionId);
}
