package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import androidx.annotation.Nullable;
import com.quran.data.model.SuraAyah;
import com.quran.data.model.selection.AyahToolBarPosition;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.PageCoordinates;
import java.util.Set;

public class AyahTrackerItem {
  final int page;

  AyahTrackerItem(int page) {
    this.page = page;
  }

  void onSetPageBounds(PageCoordinates pageCoordinates) {
  }

  void onSetAyahCoordinates(AyahCoordinates ayahCoordinates) {
  }

  boolean onHighlightAyah(int page, int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    return false;
  }

  void onHighlightAyat(int page, Set<String> ayahKeys, HighlightType type) {
  }

  void onUnHighlightAyah(int page, int sura, int ayah, HighlightType type) {
  }

  void onUnHighlightAyahType(HighlightType type) {
  }

  @Nullable
  AyahToolBarPosition getToolBarPosition(int page, int sura, int ayah,
                                                     int toolBarWidth, int toolBarHeight) {
    return null;
  }

  @Nullable
  SuraAyah getAyahForPosition(int page, float x, float y) {
    return null;
  }

  @Nullable
  QuranAyahInfo getQuranAyahInfo(int sura, int ayah) {
    return null;
  }

  @Nullable
  LocalTranslation[] getLocalTranslations() {
    return null;
  }
}
