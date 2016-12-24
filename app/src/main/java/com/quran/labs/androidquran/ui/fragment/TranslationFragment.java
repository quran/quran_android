package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.presenter.translation.TranslationPresenter;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.QuranTranslationPageLayout;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.List;
import java.util.Set;

public class TranslationFragment extends Fragment
    implements AyahTracker, TranslationPresenter.TranslationScreen {
  private static final String PAGE_NUMBER_EXTRA = "pageNumber";

  private static final String SI_PAGE_NUMBER = "SI_PAGE_NUMBER";
  private static final String SI_HIGHLIGHTED_AYAH = "SI_HIGHLIGHTED_AYAH";

  private int pageNumber;
  private int highlightedAyah;
  private TranslationView translationView;

  private QuranTranslationPageLayout mainView;

  private Resources resources;
  private QuranSettings quranSettings;
  private TranslationPresenter presenter;

  public static TranslationFragment newInstance(int page) {
    final TranslationFragment f = new TranslationFragment();
    final Bundle args = new Bundle();
    args.putInt(PAGE_NUMBER_EXTRA, page);
    f.setArguments(args);
    return f;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    pageNumber = getArguments() != null ? getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;

    Context context = getContext();
    presenter = new TranslationPresenter(context.getApplicationContext(), pageNumber);
    if (savedInstanceState != null) {
      int page = savedInstanceState.getInt(SI_PAGE_NUMBER, -1);
      if (page == pageNumber) {
        int highlightedAyah =
            savedInstanceState.getInt(SI_HIGHLIGHTED_AYAH, -1);
        if (highlightedAyah > 0) {
          this.highlightedAyah = highlightedAyah;
        }
      }
    }
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    Context context = getActivity();
    mainView = new QuranTranslationPageLayout(context);
    mainView.setPageController(null, pageNumber);
    quranSettings = QuranSettings.getInstance(context);
    resources = getResources();

    translationView = mainView.getTranslationView();
    translationView.setTranslationClickedListener(() -> {
      final Activity activity = getActivity();
      if (activity != null && activity instanceof PagerActivity) {
        ((PagerActivity) getActivity()).toggleActionBar();
      }
    });

    return mainView;
  }

  public void updateView() {
    if (getActivity() == null || resources == null || mainView == null || !isAdded()) {
      return;
    }

    final boolean nightMode = quranSettings.isNightMode();
    final boolean useNewBackground = quranSettings.useNewBackground();
    mainView.updateView(nightMode, useNewBackground, 1);
    refresh();
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type) {
    highlightAyah(sura, ayah, type, true);
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    if (translationView != null) {
      highlightedAyah = QuranInfo.getAyahId(sura, ayah);
      translationView.highlightAyah(highlightedAyah);
    }
  }

  @Override
  public AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
      int toolBarWidth, int toolBarHeight) {
    // not yet implemented
    return null;
  }

  @Override
  public void highlightAyat(
      int page, Set<String> ayahKeys, HighlightType type) {
    // not yet supported
  }

  @Override
  public void unHighlightAyah(int sura, int ayah, HighlightType type) {
    if (highlightedAyah == QuranInfo.getAyahId(sura, ayah)) {
      unHighlightAyahs(type);
    }
  }

  @Override
  public void unHighlightAyahs(HighlightType type) {
    if (translationView != null) {
      translationView.unhighlightAyat();
      highlightedAyah = -1;
    }
  }

  @Override
  public void setVerses(int page, @NonNull List<QuranAyah> verses) {
    translationView.setAyahs(verses);
    if (highlightedAyah > 0) {
      translationView.highlightAyah(highlightedAyah);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    presenter.bind(this);
    updateView();
  }

  @Override
  public void onPause() {
    presenter.unbind(this);
    super.onPause();
  }

  public void refresh() {
    presenter.refresh();
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    if (highlightedAyah > 0) {
      outState.putInt(SI_HIGHLIGHTED_AYAH, highlightedAyah);
    }
    super.onSaveInstanceState(outState);
  }
}
