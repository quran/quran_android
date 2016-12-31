package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;

import com.quran.labs.androidquran.util.QuranSettings;

public class QuranTranslationPageLayout extends QuranPageLayout {
  private com.quran.labs.androidquran.ui.translation.TranslationView translationView;

  public QuranTranslationPageLayout(Context context) {
    super(context);
  }

  @Override
  protected View generateContentView(Context context, boolean isLandscape) {
    translationView = new com.quran.labs.androidquran.ui.translation.TranslationView(context);
    return translationView;
  }

  @Override
  public void updateView(@NonNull QuranSettings quranSettings) {
    super.updateView(quranSettings);
    translationView.refresh(quranSettings);
  }

  @Override
  protected boolean shouldWrapWithScrollView() {
    return false;
  }

  public com.quran.labs.androidquran.ui.translation.TranslationView getTranslationView() {
    return translationView;
  }
}
