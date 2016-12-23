package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.BaseQuranInfo;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.model.translation.TranslationModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.List;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.DisposableSingleObserver;

public class TranslationPresenter implements Presenter<TranslationPresenter.TranslationScreen> {
  private final Context appContext;
  private final TranslationModel translationModel;

  private final VerseRange verseRange;
  private final boolean canShowArabic;

  private Disposable disposable;
  private TranslationScreen translationScreen;

  public TranslationPresenter(Context context, int page, boolean canShowArabic) {
    this(context, BaseQuranInfo.getVerseRangeForPage(page), canShowArabic);
  }

  public TranslationPresenter(Context context, VerseRange range, boolean canShowArabic) {
    this.translationModel = new TranslationModel(context.getApplicationContext());
    this.canShowArabic = canShowArabic;
    this.verseRange = range;
    this.appContext = context.getApplicationContext();
  }

  public void refresh() {
    if (disposable != null) {
      disposable.dispose();
    }

    Single<List<QuranAyah>> verses;
    final boolean getArabic = canShowArabic &&
        QuranSettings.getInstance(appContext).wantArabicInTranslationView();
    if (getArabic) {
      verses = Single.zip(
          translationModel.getArabicFromDatabase(verseRange),
          translationModel.getTranslationFromDatabase(verseRange,
              QuranSettings.getInstance(appContext).getActiveTranslation()),
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
        translationScreen.setVerses(verses);
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
    void setVerses(List<QuranAyah> verses);
  }
}
