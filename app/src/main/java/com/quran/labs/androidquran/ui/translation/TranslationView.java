package com.quran.labs.androidquran.ui.translation;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.AyahToolBar;

import java.util.ArrayList;
import java.util.List;

public class TranslationView extends FrameLayout implements View.OnClickListener,
    TranslationAdapter.OnVerseSelectedListener,
    MenuItem.OnMenuItemClickListener {
  private final TranslationAdapter translationAdapter;
  private final AyahToolBar ayahToolBar;

  private String[] translations;
  private QuranAyahInfo selectedAyah;
  private OnClickListener onClickListener;
  private OnTranslationActionListener onTranslationActionListener;
  private LinearLayoutManager layoutManager;

  public TranslationView(Context context) {
    this(context, null);
  }

  public TranslationView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TranslationView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    RecyclerView translationRecycler = new RecyclerView(context);
    layoutManager = new LinearLayoutManager(context);
    translationRecycler.setLayoutManager(layoutManager);
    translationRecycler.setItemAnimator(new DefaultItemAnimator());
    translationAdapter = new TranslationAdapter(context, translationRecycler, this, this);
    translationRecycler.setAdapter(translationAdapter);
    addView(translationRecycler, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    translationRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);

        // do not modify the RecyclerView from this method or any method called from
        // the onScrolled listener, since most modification methods cannot be called
        // while the RecyclerView is computing layout or scrolling
        if (selectedAyah != null) {
          updateAyahToolBarPosition();
        }
      }
    });

    ayahToolBar = new AyahToolBar(context, R.menu.share_menu);
    ayahToolBar.setOnItemSelectedListener(this);
    ayahToolBar.setVisibility(View.GONE);
    addView(ayahToolBar, LayoutParams.WRAP_CONTENT,
        context.getResources().getDimensionPixelSize(R.dimen.toolbar_total_height));
  }

  public void setVerses(@NonNull QuranInfo quranInfo,
                        @NonNull String[] translations,
                        @NonNull List<QuranAyahInfo> verses) {
    this.translations = translations;

    List<TranslationViewRow> rows = new ArrayList<>();
    int currentSura = -1;
    boolean wantTranslationHeaders = translations.length > 1;
    for (int i = 0, size = verses.size(); i < size; i++) {
      QuranAyahInfo verse = verses.get(i);
      int sura = verse.sura;
      if (sura != currentSura) {
        rows.add(new TranslationViewRow(TranslationViewRow.Type.SURA_HEADER, verse,
            quranInfo.getSuraName(getContext(), sura, true)));
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
    onClickListener = listener;
  }

  public void setOnTranslationActionListener(OnTranslationActionListener listener) {
    onTranslationActionListener = listener;
  }

  public void highlightAyah(int ayahId) {
    translationAdapter.setHighlightedAyah(ayahId);
  }

  public void unhighlightAyat() {
    if (selectedAyah != null) {
      selectedAyah = null;
      ayahToolBar.hideMenu();
    }
    translationAdapter.unhighlight();
  }

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    if (onTranslationActionListener != null && selectedAyah != null) {
      onTranslationActionListener.onTranslationAction(selectedAyah, translations, item.getItemId());
      return true;
    }
    return false;
  }

  @Override
  public void onClick(View v) {
    if (selectedAyah != null) {
      ayahToolBar.hideMenu();
      unhighlightAyat();
      selectedAyah = null;
    }

    if (onClickListener != null) {
      onClickListener.onClick(v);
    }
  }

  /**
   * This method updates the toolbar position when an ayah is selected
   * This method is called from the onScroll listener, and as thus must make sure not to ask
   * the RecyclerView to change anything (otherwise, it will result in a crash, as methods to
   * update the RecyclerView cannot be called amidst scrolling or computing of a layout).
   */
  private void updateAyahToolBarPosition() {
    int[] versePopupPosition = translationAdapter.getSelectedVersePopupPosition();
    if (versePopupPosition != null) {
      AyahToolBar.AyahToolBarPosition position = new AyahToolBar.AyahToolBarPosition();
      if (versePopupPosition[1] > getHeight() || versePopupPosition[1] < 0) {
        ayahToolBar.hideMenu();
      } else {
        position.x = versePopupPosition[0];
        position.y = versePopupPosition[1];
        position.pipPosition = AyahToolBar.PipPosition.UP;
        if (!ayahToolBar.isShowing()) {
          ayahToolBar.showMenu();
        }
        ayahToolBar.updatePositionRelative(position);
      }
    }
  }

  @Override
  public void onVerseSelected(QuranAyahInfo ayahInfo) {
    selectedAyah = ayahInfo;
    updateAyahToolBarPosition();
  }

  public int findFirstCompletelyVisibleItemPosition() {
    return layoutManager.findFirstCompletelyVisibleItemPosition();
  }

  public void setScrollPosition(int position) {
    layoutManager.scrollToPosition(position);
  }
}
