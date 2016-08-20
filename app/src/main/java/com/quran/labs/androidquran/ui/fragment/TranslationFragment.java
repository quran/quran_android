package com.quran.labs.androidquran.ui.fragment;

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

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.Set;

public class TranslationFragment extends Fragment
    implements AyahTracker {
  private static final String PAGE_NUMBER_EXTRA = "pageNumber";

  private static final String SI_PAGE_NUMBER = "SI_PAGE_NUMBER";
  private static final String SI_HIGHLIGHTED_AYAH = "SI_HIGHLIGHTED_AYAH";
  private static final String SI_PERCENT_VERTICAL_SCROLL_POSITION =
      "SI_PERCENT_VERTICAL_SCROLL_POSITION";

  private int mPageNumber;
  private int mHighlightedAyah;
  private TranslationView mTranslationView;

  private QuranTranslationPageLayout mMainView;

  private Resources mResources;
  private QuranSettings mQuranSettings;
  private boolean mJustCreated;
  private float mPercentLastScrollPosition;
  private ViewTreeObserver.OnGlobalLayoutListener layoutListener =
      new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
          mTranslationView.scrollTo(0,
              Math.round(mPercentLastScrollPosition * getTranslationViewHeight()));
        }
      };

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
    mPageNumber = getArguments() != null ?
        getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;
    if (savedInstanceState != null) {
      int page = savedInstanceState.getInt(SI_PAGE_NUMBER, -1);
      if (page == mPageNumber) {
        int highlightedAyah =
            savedInstanceState.getInt(SI_HIGHLIGHTED_AYAH, -1);
        if (highlightedAyah > 0) {
          mHighlightedAyah = highlightedAyah;
        }
      }
    }
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    Context context = getActivity();
    mMainView = new QuranTranslationPageLayout(context);
    mMainView.setPageController(null, mPageNumber);
    mQuranSettings = QuranSettings.getInstance(context);
    mResources = getResources();

    if (savedInstanceState != null) {
      mPercentLastScrollPosition = savedInstanceState.getFloat(SI_PERCENT_VERTICAL_SCROLL_POSITION);
    }

    mTranslationView = mMainView.getTranslationView();
    mTranslationView.setTranslationClickedListener(
        new TranslationView.TranslationClickedListener() {
          @Override
          public void onTranslationClicked() {
            final Activity activity = getActivity();
            if (activity != null && activity instanceof PagerActivity){
              ((PagerActivity) getActivity()).toggleActionBar();
            }
          }
        });
    mTranslationView.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);

    updateView();
    mJustCreated = true;

    String database = mQuranSettings.getActiveTranslation();
    refresh(database);
    return mMainView;
  }

  @Override
  public void onLoadImageResponse(BitmapDrawable drawable, Response response) {
    // no op, we're not requesting images here
  }

  public void updateView() {
    if (getActivity() == null || mResources == null ||
        mMainView == null || !isAdded()) {
      return;
    }

    final boolean nightMode = mQuranSettings.isNightMode();
    final boolean useNewBackground = mQuranSettings.useNewBackground();
    mMainView.updateView(nightMode, useNewBackground);
    if (mMainView.getTranslationView().isDataMissing()) {
      refresh(mQuranSettings.getActiveTranslation());
    }
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type) {
    highlightAyah(sura, ayah, type, true);
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    if (mTranslationView != null) {
      mHighlightedAyah = QuranInfo.getAyahId(sura, ayah);
      mTranslationView.highlightAyah(mHighlightedAyah);
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
    if (mHighlightedAyah == QuranInfo.getAyahId(sura, ayah)) {
      unHighlightAyahs(type);
    }
  }

  @Override
  public void unHighlightAyahs(HighlightType type) {
    if (mTranslationView != null) {
      mTranslationView.unhighlightAyat();
      mHighlightedAyah = -1;
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!mJustCreated) {
      updateView();
      mTranslationView.refresh();
    }
    mJustCreated = false;
  }

  public void refresh(String database) {
    if (database != null) {
      Activity activity = getActivity();
      if (activity != null) {
        new TranslationTask(activity, mPageNumber,
            mHighlightedAyah, database, mTranslationView).execute();
      }
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    if (mHighlightedAyah > 0) {
      outState.putInt(SI_HIGHLIGHTED_AYAH, mHighlightedAyah);
    }
    outState.putFloat(SI_PERCENT_VERTICAL_SCROLL_POSITION, getPercentScrollPosition());
    super.onSaveInstanceState(outState);
  }

  @Override
  public void onDetach() {
    super.onDetach();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      mTranslationView.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
    } else {
      mTranslationView.getViewTreeObserver().removeGlobalOnLayoutListener(layoutListener);
    }
    layoutListener = null;
  }

  private float getPercentScrollPosition() {
    return (float) mTranslationView.getScrollY() / getTranslationViewHeight();
  }

  private int getTranslationViewHeight() {
    return mTranslationView.getChildAt(0).getHeight();
  }
}
