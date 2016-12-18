package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.task.TranslationTask;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.QuranTranslationPageLayout;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.Set;

public class TranslationFragment extends Fragment
    implements AyahTracker {
  private static final String PAGE_NUMBER_EXTRA = "pageNumber";

  private static final String SI_PAGE_NUMBER = "SI_PAGE_NUMBER";
  private static final String SI_HIGHLIGHTED_AYAH = "SI_HIGHLIGHTED_AYAH";

  private int pageNumber;
  private int highlightedAyah;
  private TranslationView translationView;

  private QuranTranslationPageLayout mainView;

  private Resources resources;
  private QuranSettings quranSettings;
  private boolean justCreated;

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
    pageNumber = getArguments() != null ?
        getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;
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

    updateView();
    justCreated = true;

    String database = quranSettings.getActiveTranslation();
    refresh(database);
    return mainView;
  }

  @Override
  public void onLoadImageResponse(BitmapDrawable drawable, Response response) {
    // no op, we're not requesting images here
  }

  public void updateView() {
    if (getActivity() == null || resources == null ||
        mainView == null || !isAdded()) {
      return;
    }

    final boolean nightMode = quranSettings.isNightMode();
    final boolean useNewBackground = quranSettings.useNewBackground();
    mainView.updateView(nightMode, useNewBackground, 1);
    if (mainView.getTranslationView().isDataMissing()) {
      refresh(quranSettings.getActiveTranslation());
    }
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
  public void onResume() {
    super.onResume();
    if (!justCreated) {
      updateView();
      translationView.refresh();
    }
    justCreated = false;
  }

  public void refresh(String database) {
    if (database != null) {
      Activity activity = getActivity();
      if (activity != null) {
        new TranslationTask(activity, pageNumber,
            highlightedAyah, database, translationView).execute();
      }
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    if (highlightedAyah > 0) {
      outState.putInt(SI_HIGHLIGHTED_AYAH, highlightedAyah);
    }
    super.onSaveInstanceState(outState);
  }
}
