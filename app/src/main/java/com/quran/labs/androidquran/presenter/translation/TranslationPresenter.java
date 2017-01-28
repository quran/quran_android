package com.quran.labs.androidquran.presenter.translation;

import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
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
    BaseTranslationPresenter<TranslationPresenter.TranslationScreen> {
  private final Integer[] pages;
  private final QuranSettings quranSettings;

  @Inject
  TranslationPresenter(TranslationModel translationModel,
                       QuranSettings quranSettings,
                       TranslationsDBAdapter translationsAdapter,
                       Integer... pages) {
    super(translationModel, translationsAdapter);
    this.pages = pages;
    this.quranSettings = quranSettings;
  }

  public void refresh() {
    if (disposable != null) {
      disposable.dispose();
    }

    disposable = Observable.fromArray(pages)
        .flatMap(page -> getVerses(quranSettings.wantArabicInTranslationView(),
            getTranslations(quranSettings), QuranInfo.getVerseRangeForPage(page))
            .toObservable())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableObserver<ResultHolder>() {
          @Override
          public void onNext(ResultHolder result) {
            if (translationScreen != null && result.ayahInformation.size() > 0) {
              translationScreen.setVerses(
                  getPage(result.ayahInformation), result.translations, result.ayahInformation);
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

  private int getPage(List<QuranAyahInfo> result) {
    final int page;
    if (pages.length == 1) {
      page = pages[0];
    } else {
      QuranAyahInfo ayahInfo = result.get(0);
      page = QuranInfo.getPageFromSuraAyah(ayahInfo.sura, ayahInfo.ayah);
    }
    return page;
  }

  public interface TranslationScreen {
    void setVerses(int page, @NonNull String[] translations, @NonNull List<QuranAyahInfo> verses);
  }
}
