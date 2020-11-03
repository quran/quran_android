package com.quran.labs.androidquran.ui.helpers;

import androidx.annotation.Nullable;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.view.AyahToolBar;

import java.util.Set;

public interface AyahTracker {
  void highlightAyah(int sura, int ayah, HighlightType type, boolean scrollToAyah);
  void highlightAyat(int page, Set<String> ayahKeys, HighlightType type);
  void unHighlightAyah(int sura, int ayah, HighlightType type);
  void unHighlightAyahs(HighlightType type);
  AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
      int toolBarWidth, int toolBarHeight);
  @Nullable QuranAyahInfo getQuranAyahInfo(int sura, int ayah);
  @Nullable LocalTranslation[] getLocalTranslations();
}
