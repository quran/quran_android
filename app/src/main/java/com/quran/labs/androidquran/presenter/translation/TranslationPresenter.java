package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;
import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.BaseQuranInfo;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.di.QuranPageScope;
import com.quran.labs.androidquran.model.translation.TranslationModel;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.ArrayList;
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
  private TranslationsDBAdapter translationsAdapter;

  @Inject
  TranslationPresenter(Context appContext,
                       TranslationModel translationModel,
                       QuranSettings quranSettings,
                       Integer... pages) {
    super(appContext, translationModel);
    this.pages = pages;
    this.quranSettings = quranSettings;
    this.translationsAdapter = new TranslationsDBAdapter(appContext);
  }

  public void refresh() {
    final String activeTranslation = quranSettings.getActiveTranslation();
    disposable = Observable.zip(
        Observable.fromCallable(() -> translationsAdapter.getTranslations())
            .onErrorReturn(throwable -> new ArrayList<>()),
        Observable.fromArray(pages)
            .flatMap(page -> getVerses(quranSettings.wantArabicInTranslationView(),
                activeTranslation, BaseQuranInfo.getVerseRangeForPage(page))
                .toObservable()),
        (translations, ayahs) -> new ResultHolder(ayahs, translations))
        .map(resultHolder -> {
          String translator = null;
          for (int i = 0, size = resultHolder.deviceTranslations.size(); i < size; i++) {
            LocalTranslation localTranslation = resultHolder.deviceTranslations.get(i);
            if (localTranslation.filename.equals(activeTranslation)) {
              translator = localTranslation.translator;
              break;
            }
          }
          List<QuranAyah> verses = resultHolder.verseTranslations;
          if (translator != null) {
            for (int i = 0, size = verses.size(); i < size; i++) {
              verses.get(i).setTranslator(translator);
            }
          }
          return verses;
        })
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

  private static class ResultHolder {
    final List<QuranAyah> verseTranslations;
    final List<LocalTranslation> deviceTranslations;

    private ResultHolder(List<QuranAyah> verseTranslations,
                         List<LocalTranslation> deviceTranslations) {
      this.verseTranslations = verseTranslations;
      this.deviceTranslations = deviceTranslations;
    }
  }

  public interface TranslationScreen {
    void setVerses(int page, @NonNull List<QuranAyah> verses);
  }
}
