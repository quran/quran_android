package com.quran.labs.androidquran.ui.translation;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.ArrayList;
import java.util.List;

public class TranslationView extends ViewGroup {
  private final RecyclerView translationRecycler;
  private final TranslationAdapter translationAdapter;

  public TranslationView(Context context) {
    this(context, null);
  }

  public TranslationView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TranslationView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    translationRecycler = new RecyclerView(context);
    translationRecycler.setLayoutManager(new LinearLayoutManager(context));
    translationRecycler.setItemAnimator(new DefaultItemAnimator());
    translationAdapter = new TranslationAdapter(context, translationRecycler);
    translationRecycler.setAdapter(translationAdapter);
    addView(translationRecycler, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    translationRecycler.measure(widthMeasureSpec, heightMeasureSpec);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int width = translationRecycler.getMeasuredWidth();
    int x = ((r - l) - width) / 2;
    translationRecycler.layout(x, 0, x + width, getMeasuredHeight());
  }

  public void setVerses(@NonNull String[] translations, @NonNull List<QuranAyahInfo> verses) {
    List<TranslationViewRow> rows = new ArrayList<>();

    int currentSura = -1;
    boolean wantTranslationHeaders = translations.length > 1;
    for (int i = 0, size = verses.size(); i < size; i++) {
      QuranAyahInfo verse = verses.get(i);
      int sura = verse.sura;
      if (sura != currentSura) {
        rows.add(new TranslationViewRow(TranslationViewRow.Type.SURA_HEADER, verse));
        currentSura = sura;
      }

      if (verse.ayah == 1 && sura != 1 && sura != 9) {
        rows.add(new TranslationViewRow(TranslationViewRow.Type.BASMALLAH, verse));
      }

      rows.add(new TranslationViewRow(TranslationViewRow.Type.VERSE_NUMBER, verse));

      if (verse.arabicText != null) {
        rows.add(new TranslationViewRow(TranslationViewRow.Type.QURAN_TEXT, verse));
      }

      // added this to guard against a crash that happened when verse.texts was empty
      int verseTexts = verse.texts.size();
      for (int j = 0; j < translations.length; j++) {
        String text = verseTexts > j ? verse.texts.get(j) : "";
        if (!TextUtils.isEmpty(text)) {
          if (wantTranslationHeaders) {
            rows.add(
                new TranslationViewRow(TranslationViewRow.Type.TRANSLATOR, verse, translations[j]));
          }
          rows.add(new TranslationViewRow(TranslationViewRow.Type.TRANSLATION_TEXT, verse, text));
        }
      }

      rows.add(new TranslationViewRow(TranslationViewRow.Type.SPACER, verse));
    }

    translationAdapter.setData(rows);
    translationAdapter.notifyDataSetChanged();
  }

  public void refresh(@NonNull QuranSettings quranSettings) {
    translationAdapter.refresh(quranSettings);
  }

  public void setTranslationClickedListener(OnClickListener listener) {
    translationAdapter.setOnTranslationClickedListener(listener);
  }

  public void highlightAyah(int ayahId) {
    translationAdapter.setHighlightedAyah(ayahId);
  }

  public void unhighlightAyat() {
    translationAdapter.unhighlight();
  }
}
