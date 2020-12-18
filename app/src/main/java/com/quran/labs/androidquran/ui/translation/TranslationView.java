package com.quran.labs.androidquran.ui.translation;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.view.DisplayCutoutCompat;

import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.common.LocalTranslationDisplaySort;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.labs.androidquran.common.TranslationMetadata;
import com.quran.labs.androidquran.data.QuranDisplayData;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranSettings;

import com.quran.labs.androidquran.view.AyahToolBar;
import dev.chrisbanes.insetter.Insetter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class TranslationView extends FrameLayout implements View.OnClickListener,
    TranslationAdapter.OnVerseSelectedListener {
  private final TranslationAdapter translationAdapter;

  private SuraAyah selectedAyah;
  private OnClickListener onClickListener;
  private final LinearLayoutManager layoutManager;
  private PageController pageController;
  private LocalTranslation[] localTranslations;

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
      boolean isDragging = false;

      @Override
      public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);

        // do not modify the RecyclerView from this method or any method called from
        // the onScrolled listener, since most modification methods cannot be called
        // while the RecyclerView is computing layout or scrolling
        if (selectedAyah != null && isDragging) {
          updateAyahToolBarPosition();
        }
      }

      @Override
      public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        super.onScrollStateChanged(recyclerView, newState);
        isDragging = newState == RecyclerView.SCROLL_STATE_DRAGGING;
        if (selectedAyah != null && newState == RecyclerView.SCROLL_STATE_IDLE) {
          pageController.requestMenuPositionUpdate();
        }
      }
    });

    Insetter.builder()
        .setOnApplyInsetsListener((view, insets, initialState) -> {
          final DisplayCutoutCompat cutout = insets.getDisplayCutout();
          if (cutout != null) {
            final int topSafeOffset = cutout.getSafeInsetTop();
            final int bottomSafeOffset = cutout.getSafeInsetBottom();
            final int horizontalSafeOffset =
                Math.max(cutout.getSafeInsetLeft(), cutout.getSafeInsetRight());
            setPadding(horizontalSafeOffset,
                topSafeOffset,
                horizontalSafeOffset,
                bottomSafeOffset);
          }
        })
        .applyToView(this);
  }

  public void setPageController(PageController controller) {
    this.pageController = controller;
  }

  public void setVerses(@NonNull QuranDisplayData quranDisplayData,
                        @NonNull LocalTranslation[] translations,
                        @NonNull List<QuranAyahInfo> verses) {

    List<TranslationViewRow> rows = new ArrayList<>();
    int currentSura = -1;
    boolean wantTranslationHeaders = translations.length > 1;
    for (int i = 0, size = verses.size(); i < size; i++) {
      QuranAyahInfo verse = verses.get(i);
      int sura = verse.sura;
      if (sura != currentSura) {
        rows.add(new TranslationViewRow(TranslationViewRow.Type.SURA_HEADER, verse,
            quranDisplayData.getSuraName(getContext(), sura, true)));
        currentSura = sura;
      }

      if (verse.ayah == 1 && sura != 1 && sura != 9) {
        rows.add(new TranslationViewRow(TranslationViewRow.Type.BASMALLAH, verse));
      }

      rows.add(new TranslationViewRow(TranslationViewRow.Type.VERSE_NUMBER, verse));

      if (verse.arabicText != null) {
        rows.add(new TranslationViewRow(TranslationViewRow.Type.QURAN_TEXT, verse));
      }

      final LocalTranslation[] sortedTranslations = Arrays.copyOf(translations, translations.length);
      Arrays.sort(sortedTranslations, new LocalTranslationDisplaySort());

      for (int j = 0; j < sortedTranslations.length; j++) {
        final TranslationMetadata metadata = findText(verse.texts, sortedTranslations[j].getId());
        CharSequence text = metadata != null ? metadata.getText() : "";
        if (!TextUtils.isEmpty(text)) {
          if (wantTranslationHeaders) {
            rows.add(
                new TranslationViewRow(TranslationViewRow.Type.TRANSLATOR,
                    verse,
                    sortedTranslations[j].getTranslatorName()));
          }
          rows.add(new TranslationViewRow(
              TranslationViewRow.Type.TRANSLATION_TEXT, verse, text, j,
              metadata == null ? null : metadata.getLink(),
              "ar".equals(sortedTranslations[j].getLanguageCode())));
        }
      }

      rows.add(new TranslationViewRow(TranslationViewRow.Type.SPACER, verse));
    }

    localTranslations = translations;
    translationAdapter.setData(rows);
    translationAdapter.notifyDataSetChanged();
  }

  public void refresh(@NonNull QuranSettings quranSettings) {
    translationAdapter.refresh(quranSettings);
  }

  public void setTranslationClickedListener(OnClickListener listener) {
    onClickListener = listener;
  }

  public void highlightAyah(SuraAyah suraAyah, int ayahId, HighlightType highlightType) {
    if (highlightType == HighlightType.SELECTION) {
      selectedAyah = suraAyah;
    } else if (selectedAyah != null) {
      hideMenu();
    }
    translationAdapter.setHighlightedAyah(ayahId, highlightType);
  }

  private void hideMenu() {
    pageController.endAyahMode();
  }

  public void unhighlightAyah(HighlightType highlightType) {
    if (highlightType == HighlightType.SELECTION) {
      selectedAyah = null;
    }
    translationAdapter.unhighlight();
  }

  public void unhighlightAyat() {
    if (selectedAyah != null) {
      selectedAyah = null;
    }
    translationAdapter.unhighlight();
  }

  @Override
  public void onClick(View v) {
    if (selectedAyah != null) {
      hideMenu();
      selectedAyah = null;
      return;
    }

    if (onClickListener != null) {
      onClickListener.onClick(v);
    }
  }

  private TranslationMetadata findText(List<TranslationMetadata> texts, Integer translationId) {
    for (TranslationMetadata text : texts) {
      if (translationId.equals(text.getLocalTranslationId())) {
        return text;
      }
    }
    return null;
  }

  @Nullable
  public QuranAyahInfo getQuranAyahInfo(int sura, int ayah) {
    if (selectedAyah != null && selectedAyah.sura == sura && selectedAyah.ayah == ayah) {
      return translationAdapter.highlightedAyahInfo();
    } else {
      return null;
    }
  }

  @Nullable
  public LocalTranslation[] getLocalTranslations() {
    return localTranslations;
  }

  public AyahToolBar.AyahToolBarPosition getToolbarPosition() {
    int[] versePopupPosition = translationAdapter.getSelectedVersePopupPosition();
    if (versePopupPosition != null) {
      // for dual screen tablet mode, we need to add the view's x (so clicks on the
      // right page properly show on the right page and not on the left one).
      final int[] positionOnScreen = new int[2];
      getLocationOnScreen(positionOnScreen);
      final int xOffset = positionOnScreen[0];

      AyahToolBar.AyahToolBarPosition position = new AyahToolBar.AyahToolBarPosition();
      position.x = xOffset + versePopupPosition[0];
      position.y = versePopupPosition[1];
      position.pipPosition = AyahToolBar.PipPosition.UP;
      return position;
    }
    return null;
  }

  /**
   * This method updates the toolbar position when an ayah is selected
   * This method is called from the onScroll listener, and as thus must make sure not to ask
   * the RecyclerView to change anything (otherwise, it will result in a crash, as methods to
   * update the RecyclerView cannot be called amidst scrolling or computing of a layout).
   */
  private void updateAyahToolBarPosition() {
    final AyahToolBar.AyahToolBarPosition position = getToolbarPosition();
    if (position != null && (position.y > getHeight() || position.y < 0)) {
        hideMenu();
    } else {
      pageController.requestMenuPositionUpdate();
    }
  }

  @Override
  public void onVerseSelected(@NonNull QuranAyahInfo ayahInfo) {
    final SuraAyah suraAyah = new SuraAyah(ayahInfo.sura, ayahInfo.ayah);
    if (selectedAyah != null) {
      final boolean isUnselectingSelectedVerse = selectedAyah.equals(suraAyah);
      hideMenu();
      if (isUnselectingSelectedVerse) {
        return;
      }
    } else {
      // hide the menu because the previous page might have had
      // something selected here (which would break selection).
      hideMenu();
    }

    pageController.handleLongPress(suraAyah);
    pageController.requestMenuPositionUpdate();
  }

  public int findFirstCompletelyVisibleItemPosition() {
    return layoutManager.findFirstCompletelyVisibleItemPosition();
  }

  public void setScrollPosition(int position) {
    layoutManager.scrollToPosition(position);
  }
}
