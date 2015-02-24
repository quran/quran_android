package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.view.View;

public class QuranTranslationPageLayout extends QuranPageLayout {
  private TranslationView mTranslationView;

  public QuranTranslationPageLayout(Context context) {
    super(context);
  }

  @Override
  protected View generateContentView(Context context) {
    mTranslationView = new TranslationView(context);
    return mTranslationView;
  }

  @Override
  protected void setContentNightMode(boolean nightMode, int textBrightness) {
    mTranslationView.setNightMode(nightMode, textBrightness);
  }

  @Override
  protected boolean shouldWrapWithScrollView() {
    return false;
  }

  public TranslationView getTranslationView() {
    return mTranslationView;
  }
}
