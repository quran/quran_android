package com.quran.labs.androidquran.presenter.translation;

import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.BaseQuranInfo;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.di.QuranPageScope;
import com.quran.labs.androidquran.model.translation.TranslationModel;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;

@QuranPageScope
public class TranslationPresenter extends
    AbstractTranslationPresenter<TranslationPresenter.TranslationScreen> {
  private final Integer[] pages;
  private final QuranSettings quranSettings;
  private final TranslationsDBAdapter translationsAdapter;
  private final Map<String, LocalTranslation> translationMap;


  @Inject
  TranslationPresenter(TranslationModel translationModel,
                       QuranSettings quranSettings,
                       TranslationsDBAdapter translationsAdapter,
                       Integer... pages) {
    super(translationModel);
    this.pages = pages;
    this.quranSettings = quranSettings;
    this.translationMap = new HashMap<>();
    this.translationsAdapter = translationsAdapter;
  }

  public void refresh() {
    if (disposable != null) {
      disposable.dispose();
    }

    final String activeTranslation = quranSettings.getActiveTranslation();
    disposable =
        getTranslationMapCompletable()
            .andThen(Observable.fromArray(pages)
                .flatMap(page -> getVerses(quranSettings.wantArabicInTranslationView(),
                    activeTranslation, BaseQuranInfo.getVerseRangeForPage(page))
                    .toObservable()))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableObserver<List<QuranAyah>>() {
              @Override
              public void onNext(List<QuranAyah> result) {
                if (translationScreen != null && result.size() > 0) {
                  result = populateTranslator(result, activeTranslation);
                  translationScreen.setVerses(getPage(result), result);
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

  private int getPage(List<QuranAyah> result) {
    final int page;
    if (pages.length == 1) {
      page = pages[0];
    } else {
      QuranAyah ayah = result.get(0);
      page = QuranInfo.getPageFromSuraAyah(ayah.getSura(), ayah.getAyah());
    }
    return page;
  }

  private List<QuranAyah> populateTranslator(List<QuranAyah> result, String activeTranslation) {
    LocalTranslation localTranslation = translationMap.get(activeTranslation);
    String translator = localTranslation == null ?
        null : localTranslation.translator;
    if (translator != null && result.size() > 0) {
      for (int i = 0, size = result.size(); i < size; i++) {
        QuranAyah ayah = result.get(i);
        ayah.setTranslator(translator);
      }
    }
    return result;
  }

  @NonNull
  private Completable getTranslationMapCompletable() {
    if (this.translationMap.size() == 0) {
      return Single.fromCallable(() -> translationsAdapter.getTranslations())
          .map(translations -> {
            Map<String, LocalTranslation> map = new HashMap<>();
            for (int i = 0, size = translations.size(); i < size; i++) {
              LocalTranslation translation = translations.get(i);
              map.put(translation.filename, translation);
            }
            return map;
          })
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .doOnSuccess(map -> {
            this.translationMap.clear();
            this.translationMap.putAll(map);
          })
          .toCompletable();
    } else {
      return Completable.complete();
    }
  }

  public interface TranslationScreen {
    void setVerses(int page, @NonNull List<QuranAyah> verses);
  }
}
