package com.quran.labs.androidquran.presenter.translation;

import android.content.Context;

import com.quran.labs.androidquran.data.BaseQuranInfo;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.util.QuranSettings;

public class TranslationPresenter extends AbstractTranslationPresenter {
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
}
