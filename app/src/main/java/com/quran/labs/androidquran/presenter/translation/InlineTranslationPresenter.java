package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;
import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.model.translation.TranslationModel;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableSingleObserver;

public class InlineTranslationPresenter extends
    AbstractTranslationPresenter<InlineTranslationPresenter.TranslationScreen> {

  @Inject
  public InlineTranslationPresenter(Context appContext, TranslationModel translationModel) {
    super(appContext, translationModel);
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
