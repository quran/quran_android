package com.quran.labs.androidquran.presenter.translation;

import android.support.annotation.Nullable;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.model.translation.TranslationModel;
import com.quran.labs.androidquran.presenter.Presenter;

import java.util.List;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

abstract class AbstractTranslationPresenter<T> implements Presenter<T> {
  private final TranslationModel translationModel;

  @Nullable T translationScreen;
  Disposable disposable;

  AbstractTranslationPresenter(TranslationModel translationModel) {
    this.translationModel = translationModel;
  }

  Single<List<QuranAyah>> getVerses(boolean getArabic,
                                    String activeTranslation,
                                    VerseRange verseRange) {
    if (getArabic) {
      return Single.zip(
          translationModel.getArabicFromDatabase(verseRange),
          translationModel.getTranslationFromDatabase(verseRange, activeTranslation),
          (arabic, translation) -> {
            int size = translation.size();
            if (arabic.size() == size) {
              for (int i = 0; i < size; i++) {
                translation.get(i).setText(arabic.get(i).getText());
              }
            }
            return translation;
          })
          .subscribeOn(Schedulers.io());
    } else {
      return translationModel.getTranslationFromDatabase(verseRange, activeTranslation)
          .subscribeOn(Schedulers.io());
    }
  }

  @Override
  public void bind(T what) {
    translationScreen = what;
  }

  @Override
  public void unbind(T what) {
    translationScreen = null;
    if (disposable != null) {
      disposable.dispose();
    }
  }
}
