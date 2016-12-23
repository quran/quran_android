package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.BaseQuranInfo;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.List;

public class TranslationPresenter extends
    AbstractTranslationPresenter<TranslationPresenter.TranslationScreen> {
  private final int page;
  private final VerseRange verseRange;
  private final QuranSettings quranSettings;

  public TranslationPresenter(Context context, int page) {
    super(context);
    this.page = page;
    this.verseRange = BaseQuranInfo.getVerseRangeForPage(page);
    this.quranSettings = QuranSettings.getInstance(context.getApplicationContext());
  }

  public void refresh() {
    getVerses(quranSettings.wantArabicInTranslationView(),
        quranSettings.getActiveTranslation(), verseRange);
  }

  @Override
  void onData(@Nullable TranslationScreen translationScreen, @NonNull List<QuranAyah> verses) {
    if (translationScreen != null) {
      translationScreen.setVerses(page, verses);
    }
  }

  public interface TranslationScreen {
    void setVerses(int page, @NonNull List<QuranAyah> verses);
  }
}
