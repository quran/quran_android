package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;
import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.BaseQuranInfo;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.di.QuranPageScope;
import com.quran.labs.androidquran.model.translation.TranslationModel;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableObserver;

@QuranPageScope
public class TranslationPresenter extends
    AbstractTranslationPresenter<TranslationPresenter.TranslationScreen> {
  private final Integer[] pages;
  private final QuranSettings quranSettings;

  @Inject
  public TranslationPresenter(Context appContext,
                              TranslationModel translationModel,
                              QuranSettings quranSettings,
                              Integer... pages) {
    super(appContext, translationModel);
    this.pages = pages;
    this.quranSettings = quranSettings;
  }

  public void refresh() {
    disposable = Observable.fromArray(pages)
        .flatMap(page -> getVerses(quranSettings.wantArabicInTranslationView(),
            quranSettings.getActiveTranslation(), BaseQuranInfo.getVerseRangeForPage(page))
            .toObservable())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableObserver<List<QuranAyah>>() {
          @Override
          public void onNext(List<QuranAyah> result) {
            if (translationScreen != null && result.size() > 0) {
              final int page;
              if (pages.length == 1) {
                page = pages[0];
              } else {
                QuranAyah ayah = result.get(0);
                page = QuranInfo.getPageFromSuraAyah(ayah.getSura(), ayah.getAyah());
              }
              translationScreen.setVerses(page, result);
            }
          }

          @Override
          public void onError(Throwable e) {
          }

          @Override
          public void onComplete() {
          }
        });
  }

  public interface TranslationScreen {
    void setVerses(int page, @NonNull List<QuranAyah> verses);
  }
}
