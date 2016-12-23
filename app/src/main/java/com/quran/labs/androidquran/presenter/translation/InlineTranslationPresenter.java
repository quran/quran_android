package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;

import com.quran.labs.androidquran.data.VerseRange;

public class InlineTranslationPresenter extends AbstractTranslationPresenter {

  public InlineTranslationPresenter(Context context) {
    super(context);
  }

  public void refresh(VerseRange verseRange, String activeTranslation) {
    getVerses(false, activeTranslation, verseRange);
  }
}
