package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;
import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.model.translation.TranslationModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.List;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;

class AbstractTranslationPresenter implements
    Presenter<AbstractTranslationPresenter.TranslationScreen> {
  private final Context appContext;
  private final TranslationModel translationModel;

  private Disposable disposable;
  private InlineTranslationPresenter.TranslationScreen translationScreen;

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
        if (translationScreen != null) {
          translationScreen.setVerses(verses);
        }
      }

      @Override
      public void onError(Throwable e) {
      }
    });
  }

  @Override
  public void bind(TranslationScreen what) {
    translationScreen = what;
  }

  @Override
  public void unbind(TranslationScreen what) {
    translationScreen = null;
  }

  public interface TranslationScreen {
    void setVerses(@NonNull List<QuranAyah> verses);
  }
}
