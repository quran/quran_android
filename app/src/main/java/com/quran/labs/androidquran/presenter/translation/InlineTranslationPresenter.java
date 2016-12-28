package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;
import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.VerseRange;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableSingleObserver;

public class InlineTranslationPresenter extends
    AbstractTranslationPresenter<InlineTranslationPresenter.TranslationScreen> {

  public InlineTranslationPresenter(Context appContext) {
    super(appContext);
  }

  public void refresh(VerseRange verseRange, String activeTranslation) {
    getVerses(false, activeTranslation, verseRange)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableSingleObserver<List<QuranAyah>>() {
          @Override
          public void onSuccess(List<QuranAyah> verses) {
            if (translationScreen != null) {
              translationScreen.setVerses(verses);
            }
          }

          @Override
          public void onError(Throwable e) {
          }
        });
  }

  public interface TranslationScreen {
    void setVerses(@NonNull List<QuranAyah> verses);
  }
}
