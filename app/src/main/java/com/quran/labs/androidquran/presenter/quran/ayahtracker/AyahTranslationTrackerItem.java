package com.quran.labs.androidquran.presenter.quran.ayahtracker;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import com.quran.data.core.QuranInfo;
import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.translation.TranslationView;
import com.quran.labs.androidquran.view.AyahToolBar;
import com.quran.labs.androidquran.view.HighlightingImageView;

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
      ayahView.highlightAyah(new SuraAyah(sura, ayah), quranInfo.getAyahId(sura, ayah));
      return true;
    }
    ayahView.unhighlightAyat();
    return false;
  }

  @Override
  void onUnHighlightAyah(int page, int sura, int ayah, HighlightType type) {
    if (this.page == page) {
      ayahView.unhighlightAyat();
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
}
