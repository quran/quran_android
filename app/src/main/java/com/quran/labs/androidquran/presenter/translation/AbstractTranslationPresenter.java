package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.model.translation.TranslationModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.List;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;

abstract class AbstractTranslationPresenter<T> implements Presenter<T> {
  private final Context appContext;
  private final TranslationModel translationModel;

  private @Nullable Disposable disposable;
  private @Nullable T translationScreen;

  AbstractTranslationPresenter(Context context) {
    appContext = context.getApplicationContext();
    translationModel = new TranslationModel(appContext);
  }

  void getVerses(boolean getArabic, String activeTranslation, VerseRange verseRange) {
    if (disposable != null) {
      disposable.dispose();
    }

    Single<List<QuranAyah>> verses;
    if (getArabic) {
      verses = Single.zip(
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
          });
    } else {
      verses = translationModel.getTranslationFromDatabase(
          verseRange, QuranSettings.getInstance(appContext).getActiveTranslation());
    }

    disposable = verses.subscribeWith(new DisposableSingleObserver<List<QuranAyah>>() {
      @Override
      public void onSuccess(List<QuranAyah> verses) {
        onData(translationScreen, verses);
      }

      @Override
      public void onError(Throwable e) {
      }
    });
  }

  abstract void onData(@Nullable T translationScreen, @NonNull List<QuranAyah> verses);

  @Override
  public void bind(T what) {
    translationScreen = what;
  }

  @Override
  public void unbind(T what) {
    translationScreen = null;
  }
}
