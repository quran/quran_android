package com.quran.labs.androidquran.presenter.translation;

import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.model.translation.TranslationModel;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableSingleObserver;

public class InlineTranslationPresenter extends
    BaseTranslationPresenter<InlineTranslationPresenter.TranslationScreen> {

  @Inject
  InlineTranslationPresenter(TranslationModel translationModel,
                             TranslationsDBAdapter dbAdapter) {
    super(translationModel, dbAdapter);
  }

  public void refresh(VerseRange verseRange, String activeTranslation) {
    if (disposable != null) {
      disposable.dispose();
    }

    disposable = getVerses(false, Collections.singletonList(activeTranslation), verseRange)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableSingleObserver<ResultHolder>() {
          @Override
          public void onSuccess(ResultHolder result) {
            if (translationScreen != null) {
              translationScreen.setVerses(result.ayahInformation);
            }
          }

          @Override
          public void onError(Throwable e) {
          }
        });
  }

  public interface TranslationScreen {
    void setVerses(@NonNull List<QuranAyahInfo> verses);
  }
}
