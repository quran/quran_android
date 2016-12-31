package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.view.View;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.translation.TranslationView;
import com.quran.labs.androidquran.util.QuranSettings;

public class QuranTranslationPageLayout extends QuranPageLayout {
  private TranslationView translationView;

  public QuranTranslationPageLayout(Context context) {
    super(context);
    isFullWidth = true;
  }

  @Override
  protected View generateContentView(Context context, boolean isLandscape) {
    translationView = new TranslationView(context);
    return translationView;
  }

  @Override
  public void updateView(@NonNull QuranSettings quranSettings) {
    super.updateView(quranSettings);
    translationView.refresh(quranSettings);
  }

  @Override
  protected void updateBackground(boolean nightMode, QuranSettings quranSettings) {
    if (nightMode) {
      setBackgroundResource(R.color.translation_background_color_night);
    } else {
      setBackgroundColor(Color.WHITE);
    }
  }

  @Override
  protected boolean shouldWrapWithScrollView() {
    return false;
  }

  public TranslationView getTranslationView() {
    return translationView;
  }
}
