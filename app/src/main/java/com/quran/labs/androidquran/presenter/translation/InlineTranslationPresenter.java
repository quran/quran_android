package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.VerseRange;

import java.util.List;

public class InlineTranslationPresenter extends
    AbstractTranslationPresenter<InlineTranslationPresenter.TranslationScreen> {

  public InlineTranslationPresenter(Context context) {
    super(context);
  }

  @Override
  void onData(@Nullable TranslationScreen translationScreen, @NonNull List<QuranAyah> verses) {
    if (translationScreen != null) {
      translationScreen.setVerses(verses);
    }
  }

  public void refresh(VerseRange verseRange, String activeTranslation) {
    getVerses(false, activeTranslation, verseRange);
  }

  public interface TranslationScreen {
    void setVerses(@NonNull List<QuranAyah> verses);
  }
}
