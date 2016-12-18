package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.view.View;

public class QuranTranslationPageLayout extends QuranPageLayout {
  private TranslationView translationView;

  public QuranTranslationPageLayout(Context context) {
    super(context);
  }

  @Override
  protected View generateContentView(Context context, boolean isLandscape) {
    translationView = new TranslationView(context);
    return translationView;
  }

  @Override
  protected void setContentNightMode(boolean nightMode, int textBrightness) {
    translationView.setNightMode(nightMode, textBrightness);
  }

  @Override
  protected boolean shouldWrapWithScrollView() {
    return false;
  }

  public TranslationView getTranslationView() {
    return translationView;
  }
}
