package com.quran.labs.androidquran.presenter.translation;

import androidx.annotation.NonNull;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.TranslationsDBAdapter;
import com.quran.labs.androidquran.di.QuranPageScope;
import com.quran.labs.androidquran.model.translation.TranslationModel;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.ShareUtil;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableObserver;

@QuranPageScope
public class TranslationPresenter extends
    BaseTranslationPresenter<TranslationPresenter.TranslationScreen> {
  private final Integer[] pages;
  private final ShareUtil shareUtil;
  private final QuranSettings quranSettings;
  private final QuranInfo quranInfo;

  @Inject
  TranslationPresenter(TranslationModel translationModel,
                       QuranSettings quranSettings,
                       TranslationsDBAdapter translationsAdapter,
                       ShareUtil shareUtil,
                       QuranInfo quranInfo,
                       Integer... pages) {
    super(translationModel, translationsAdapter, quranInfo);
    this.pages = pages;
    this.quranInfo = quranInfo;
    this.shareUtil = shareUtil;
    this.quranSettings = quranSettings;
  }

  public void refresh() {
    if (disposable != null) {
      disposable.dispose();
    }

    disposable = Observable.fromArray(pages)
        .flatMap(page -> getVerses(quranSettings.wantArabicInTranslationView(),
            getTranslations(quranSettings), quranInfo.getVerseRangeForPage(page))
            .toObservable())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(new DisposableObserver<ResultHolder>() {
          @Override
          public void onNext(ResultHolder result) {
            if (translationScreen != null && result.ayahInformation.size() > 0) {
              translationScreen.setVerses(
                  getPage(result.ayahInformation), result.translations, result.ayahInformation);
              translationScreen.updateScrollPosition();
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

  public void onTranslationAction(PagerActivity activity,
                                  QuranAyahInfo ayah,
                                  String[] translationNames,
                                  int actionId) {
    switch (actionId) {
      case R.id.cab_share_ayah_link: {
        SuraAyah bounds = new SuraAyah(ayah.sura, ayah.ayah);
        activity.shareAyahLink(bounds, bounds);
        break;
      }
      case R.id.cab_share_ayah_text:
      case R.id.cab_copy_ayah: {
        String shareText = shareUtil.getShareText(activity, ayah, translationNames);
        if (actionId == R.id.cab_share_ayah_text) {
          shareUtil.shareViaIntent(activity, shareText, R.string.share_ayah_text);
        } else {
          shareUtil.copyToClipboard(activity, shareText);
        }
        break;
      }
    }
  }

  private int getPage(List<QuranAyahInfo> result) {
    final int page;
    if (pages.length == 1) {
      page = pages[0];
    } else {
      QuranAyahInfo ayahInfo = result.get(0);
      page = quranInfo.getPageFromSuraAyah(ayahInfo.sura, ayahInfo.ayah);
    }
    return page;
  }

  public interface TranslationScreen {
    void setVerses(int page, @NonNull String[] translations, @NonNull List<QuranAyahInfo> verses);
    void updateScrollPosition();
  }
}
