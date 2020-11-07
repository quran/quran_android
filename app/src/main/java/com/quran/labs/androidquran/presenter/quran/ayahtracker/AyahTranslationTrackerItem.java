package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import com.quran.data.core.QuranInfo;
import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.translation.TranslationView;
import com.quran.labs.androidquran.view.AyahToolBar;

public class AyahTranslationTrackerItem extends AyahTrackerItem {
  private final QuranInfo quranInfo;
  @NonNull private final TranslationView ayahView;

  public AyahTranslationTrackerItem(int page,
                                    QuranInfo quranInfo,
                                    @NonNull TranslationView ayahView) {
    super(page);
    this.ayahView = ayahView;
    this.quranInfo = quranInfo;
  }

  @Override
  boolean onHighlightAyah(int page, int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    if (this.page == page) {
      ayahView.highlightAyah(new SuraAyah(sura, ayah), quranInfo.getAyahId(sura, ayah), type);
      return true;
    }
    ayahView.unhighlightAyah(type);
    return false;
  }

  @Override
  void onUnHighlightAyah(int page, int sura, int ayah, HighlightType type) {
    if (this.page == page) {
      ayahView.unhighlightAyah(type);
    }
  }

  @Override
  void onUnHighlightAyahType(HighlightType type) {
    ayahView.unhighlightAyat();
  }

  @Nullable
  @Override
  AyahToolBar.AyahToolBarPosition getToolBarPosition(int page,
      int sura, int ayah, int toolBarWidth, int toolBarHeight) {
    final AyahToolBar.AyahToolBarPosition position = ayahView.getToolbarPosition();
    return position == null ? super.getToolBarPosition(page, sura, ayah, toolBarWidth,
        toolBarHeight) : position;
  }

  @Nullable
  @Override
  QuranAyahInfo getQuranAyahInfo(int sura, int ayah) {
    final QuranAyahInfo quranAyahInfo = ayahView.getQuranAyahInfo(sura, ayah);
    return quranAyahInfo == null ? super.getQuranAyahInfo(sura, ayah) : quranAyahInfo;
  }

  @Nullable
  LocalTranslation[] getLocalTranslations() {
    final LocalTranslation[] translations = ayahView.getLocalTranslations();
    return translations == null ? super.getLocalTranslations() : translations;  }
}
