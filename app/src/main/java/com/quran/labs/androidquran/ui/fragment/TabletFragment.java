package com.quran.labs.androidquran.ui.fragment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import com.quran.data.core.QuranInfo;
import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.common.QuranAyahInfo;
import com.quran.data.model.bookmark.Bookmark;
import com.quran.labs.androidquran.data.QuranDisplayData;
import com.quran.labs.androidquran.di.module.fragment.QuranPageModule;
import com.quran.labs.androidquran.presenter.quran.QuranPagePresenter;
import com.quran.labs.androidquran.presenter.quran.QuranPageScreen;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahImageTrackerItem;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahSplitConsolidationTrackerItem;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerItem;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTranslationTrackerItem;
import com.quran.labs.androidquran.presenter.translation.TranslationPresenter;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.QuranPage;
import com.quran.labs.androidquran.ui.translation.TranslationView;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.view.HighlightingImageView;
import com.quran.labs.androidquran.view.QuranImagePageLayout;
import com.quran.labs.androidquran.view.QuranTranslationPageLayout;
import com.quran.labs.androidquran.view.TabletView;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.PageCoordinates;
import com.quran.page.common.draw.ImageDrawHelper;
import dagger.Lazy;
import io.reactivex.disposables.CompositeDisposable;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import timber.log.Timber;

import static com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType;

public class TabletFragment extends Fragment
    implements PageController, TranslationPresenter.TranslationScreen,
    QuranPage, QuranPageScreen, AyahTrackerPresenter.AyahInteractionHandler {
  private static final String FIRST_PAGE_EXTRA = "pageNumber";
  private static final String MODE_EXTRA = "mode";
  private static final String IS_SPLIT_SCREEN = "splitScreenMode";
  private static final String SI_RIGHT_TRANSLATION_SCROLL_POSITION
      = "SI_RIGHT_TRANSLATION_SCROLL_POSITION";

  public static class Mode {
    public static final int ARABIC = 1;
    public static final int TRANSLATION = 2;
  }

  private int mode;
  private int pageNumber;
  private int translationScrollPosition;
  private boolean ayahCoordinatesError;
  private boolean isSplitScreen = false;
  private boolean isQuranOnRight = true;

  private TabletView mainView;
  private TranslationView leftTranslation;
  private TranslationView rightTranslation;
  private HighlightingImageView leftImageView;
  private HighlightingImageView rightImageView;
  private final CompositeDisposable compositeDisposable = new CompositeDisposable();
  private AyahTrackerItem[] ayahTrackerItems;

  private TranslationView splitTranslationView;
  private HighlightingImageView splitImageView;
  private int lastLongPressPage = -1;

  @Inject QuranSettings quranSettings;
  @Inject AyahTrackerPresenter ayahTrackerPresenter;
  @Inject Lazy<QuranPagePresenter> quranPagePresenter;
  @Inject Lazy<TranslationPresenter> translationPresenter;
  @Inject AyahSelectedListener ayahSelectedListener;
  @Inject QuranScreenInfo quranScreenInfo;
  @Inject QuranInfo quranInfo;
  @Inject QuranDisplayData quranDisplayData;
  @Inject Set<ImageDrawHelper> imageDrawHelpers;

  public static TabletFragment newInstance(int firstPage, int mode, boolean isSplitScreen) {
    final TabletFragment f = new TabletFragment();
    final Bundle args = new Bundle();
    args.putInt(FIRST_PAGE_EXTRA, firstPage);
    args.putInt(MODE_EXTRA, mode);
    args.putBoolean(IS_SPLIT_SCREEN, isSplitScreen);
    f.setArguments(args);
    return f;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (savedInstanceState != null) {
      translationScrollPosition = savedInstanceState.getInt(
          SI_RIGHT_TRANSLATION_SCROLL_POSITION);
    }
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final Context context = getActivity();
    mainView = new TabletView(context);

    if (mode == Mode.ARABIC) {
      mainView.init(TabletView.QURAN_PAGE, TabletView.QURAN_PAGE);
      leftImageView = ((QuranImagePageLayout) mainView.getLeftPage()).getImageView();
      rightImageView = ((QuranImagePageLayout) mainView.getRightPage()).getImageView();
      mainView.setPageController(this, pageNumber, pageNumber - 1);
    } else if (mode == Mode.TRANSLATION) {
      if (!isSplitScreen) {
        mainView.init(TabletView.TRANSLATION_PAGE, TabletView.TRANSLATION_PAGE);
        leftTranslation =
            ((QuranTranslationPageLayout) mainView.getLeftPage()).getTranslationView();
        rightTranslation =
            ((QuranTranslationPageLayout) mainView.getRightPage()).getTranslationView();

        PagerActivity pagerActivity = (PagerActivity) context;
        leftTranslation.setTranslationClickedListener(v -> pagerActivity.toggleActionBar());
        rightTranslation.setTranslationClickedListener(v -> pagerActivity.toggleActionBar());
        mainView.setPageController(this, pageNumber, pageNumber - 1);
      } else {
        initSplitMode();
      }
    }
    return mainView;
  }

  private void initSplitMode() {
    isQuranOnRight = pageNumber % 2 == 1;

    final int leftPageType = isQuranOnRight ? TabletView.TRANSLATION_PAGE : TabletView.QURAN_PAGE;
    final int rightPageType = isQuranOnRight ? TabletView.QURAN_PAGE : TabletView.TRANSLATION_PAGE;

    mainView.init(leftPageType, rightPageType);

    if (isQuranOnRight) {
      splitTranslationView =
          ((QuranTranslationPageLayout) mainView.getLeftPage()).getTranslationView();
      splitImageView =
          ((QuranImagePageLayout) mainView.getRightPage()).getImageView();
    } else {
      splitImageView =
          ((QuranImagePageLayout) mainView.getLeftPage()).getImageView();
      splitTranslationView =
          ((QuranTranslationPageLayout) mainView.getRightPage()).getTranslationView();
    }

    PagerActivity pagerActivity = (PagerActivity) getActivity();
    splitTranslationView.setTranslationClickedListener(v -> pagerActivity.toggleActionBar());
    mainView.setPageController(this, pageNumber);
  }

  @Override
  public void onStart() {
    super.onStart();
    ayahTrackerPresenter.bind(this);
    if (mode == Mode.ARABIC) {
      quranPagePresenter.get().bind(this);
    } else {
      if (isSplitScreen) {
        translationPresenter.get().bind(this);
        quranPagePresenter.get().bind(this);
      } else {
        translationPresenter.get().bind(this);
      }
    }
  }

  @Override
  public void onPause() {
    if (mode == Mode.TRANSLATION) {
      if (isSplitScreen) {
        translationScrollPosition = splitTranslationView.findFirstCompletelyVisibleItemPosition();
      } else {
        translationScrollPosition = rightTranslation
            .findFirstCompletelyVisibleItemPosition();
      }
    }
    super.onPause();
  }

  @Override
  public void onStop() {
    ayahTrackerPresenter.unbind(this);
    if (mode == Mode.ARABIC) {
      quranPagePresenter.get().unbind(this);
    } else {
      if (isSplitScreen) {
        translationPresenter.get().unbind(this);
        quranPagePresenter.get().unbind(this);
      } else {
        translationPresenter.get().unbind(this);
      }
    }
    super.onStop();
  }

  @Override
  public void onResume() {
    super.onResume();
    updateView();
    if (mode == Mode.TRANSLATION) {
      if (isSplitScreen) {
        splitTranslationView.refresh(quranSettings);
      } else {
        rightTranslation.refresh(quranSettings);
        leftTranslation.refresh(quranSettings);
      }
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    if (mode == Mode.TRANSLATION) {
      if (isSplitScreen) {
        outState.putInt(SI_RIGHT_TRANSLATION_SCROLL_POSITION,
            splitTranslationView.findFirstCompletelyVisibleItemPosition());
      } else {
        outState.putInt(SI_RIGHT_TRANSLATION_SCROLL_POSITION,
            rightTranslation.findFirstCompletelyVisibleItemPosition());
      }
    }
    super.onSaveInstanceState(outState);
  }

  @Override
  public void updateView() {
    if (isAdded()) {
      mainView.updateView(quranSettings);
    }
  }

  @Override
  public AyahTracker getAyahTracker() {
    return ayahTrackerPresenter;
  }

  @Override
  public AyahTrackerItem[] getAyahTrackerItems() {
    if (ayahTrackerItems == null) {
      AyahTrackerItem left;
      AyahTrackerItem right;
      final int screenHeight = quranScreenInfo.getHeight();
      if (mode == Mode.ARABIC) {
        left = new AyahImageTrackerItem(pageNumber,
            screenHeight,
            quranInfo,
            quranDisplayData,
            false,
            imageDrawHelpers,
            leftImageView);
        right = new AyahImageTrackerItem(
            pageNumber - 1, screenHeight, quranInfo, quranDisplayData, true, imageDrawHelpers,
            rightImageView);
      } else if (mode == Mode.TRANSLATION) {
        if (isSplitScreen) {
          final AyahImageTrackerItem imageItem;
          final AyahTranslationTrackerItem translationItem;
          if (isQuranOnRight) {
            translationItem = new AyahTranslationTrackerItem(pageNumber, quranInfo, splitTranslationView);
            imageItem = new AyahImageTrackerItem(pageNumber,
                screenHeight,
                quranInfo,
                quranDisplayData,
                true,
                imageDrawHelpers,
                splitImageView);
          } else {
            imageItem = new AyahImageTrackerItem(pageNumber,
                screenHeight,
                quranInfo,
                quranDisplayData,
                false,
                imageDrawHelpers,
                splitImageView);
            translationItem = new AyahTranslationTrackerItem(pageNumber, quranInfo, splitTranslationView);
          }
          final AyahTrackerItem splitItem =
              new AyahSplitConsolidationTrackerItem(pageNumber, imageItem, translationItem);
          ayahTrackerItems = new AyahTrackerItem[] { splitItem };
          return ayahTrackerItems;
        } else {
          left = new AyahTranslationTrackerItem(pageNumber, quranInfo, leftTranslation);
          right = new AyahTranslationTrackerItem(pageNumber - 1, quranInfo, rightTranslation);
        }
      } else {
        return new AyahTrackerItem[0];
      }
      ayahTrackerItems = new AyahTrackerItem[]{ right, left };
    }
    return ayahTrackerItems;
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    pageNumber = getArguments().getInt(FIRST_PAGE_EXTRA);
    mode = getArguments().getInt(MODE_EXTRA, Mode.ARABIC);
    isSplitScreen = getArguments().getBoolean(IS_SPLIT_SCREEN, false);

    final Integer[] pages = (isSplitScreen && mode == Mode.TRANSLATION) ?
        new Integer[]{ pageNumber } : new Integer[]{ pageNumber - 1, pageNumber };

    ((PagerActivity) getActivity()).getPagerActivityComponent()
        .quranPageComponentBuilder()
        .withQuranPageModule(new QuranPageModule(pages))
        .build()
        .inject(this);
  }

  @Override
  public void onDetach() {
    super.onDetach();
    ayahSelectedListener = null;
    compositeDisposable.clear();
  }

  @Override
  public void setPageDownloadError(@StringRes int errorMessage) {
    mainView.showError(errorMessage);
    mainView.setOnClickListener(v -> ayahSelectedListener.onClick(EventType.SINGLE_TAP));
  }

  @Override
  public void setPageBitmap(int page, @NonNull Bitmap pageBitmap) {
    if (isSplitScreen && mode == Mode.TRANSLATION) {
      splitImageView.setImageDrawable(new BitmapDrawable(getResources(), pageBitmap));
    } else {
      ImageView imageView = page == pageNumber - 1 ? rightImageView : leftImageView;
      imageView.setImageDrawable(new BitmapDrawable(getResources(), pageBitmap));
    }
  }

  @Override
  public void hidePageDownloadError() {
    mainView.hideError();
    mainView.setOnClickListener(null);
    mainView.setClickable(false);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    if (mode == Mode.TRANSLATION) {
      translationPresenter.get().refresh();
    }
  }

  @Override
  public void setVerses(int page,
                        @NonNull LocalTranslation[] translations,
                        @NonNull List<QuranAyahInfo> verses) {
    if (isSplitScreen) {
      splitTranslationView.setVerses(quranDisplayData, translations, verses);
    } else {
      if (page == pageNumber) {
        leftTranslation.setVerses(quranDisplayData, translations, verses);
      } else if (page == pageNumber - 1) {
        rightTranslation.setVerses(quranDisplayData, translations, verses);
      }
    }
  }

  @Override
  public void updateScrollPosition() {
    if (isSplitScreen) {
      splitTranslationView.setScrollPosition(translationScrollPosition);
    } else {
      rightTranslation.setScrollPosition(translationScrollPosition);
    }
  }

  public void refresh() {
    if (mode == Mode.TRANSLATION) {
      translationPresenter.get().refresh();
    }
  }

  public void cleanup() {
    Timber.d("cleaning up page %d", pageNumber);
    if (leftImageView != null) {
      leftImageView.setImageDrawable(null);
    }

    if (rightImageView != null) {
      rightImageView.setImageDrawable(null);
    }

    if (splitImageView != null) {
      splitImageView.setImageDrawable(null);
    }
  }

  @Override
  public void setBookmarksOnPage(List<Bookmark> bookmarks) {
    ayahTrackerPresenter.setAyahBookmarks(bookmarks);
  }

  @Override
  public void setPageCoordinates(PageCoordinates pageCoordinates) {
    ayahTrackerPresenter.setPageBounds(pageCoordinates);
  }

  @Override
  public void setAyahCoordinatesError() {
    ayahCoordinatesError = true;
  }

  @Override
  public void setAyahCoordinatesData(AyahCoordinates ayahCoordinates) {
    ayahTrackerPresenter.setAyahCoordinates(ayahCoordinates);
  }

  @Override
  public boolean handleTouchEvent(MotionEvent event, EventType eventType, int page) {
    return isVisible() && ayahTrackerPresenter.handleTouchEvent(getActivity(), event, eventType,
        page, ayahSelectedListener, ayahCoordinatesError);
  }

  @Override
  public void handleLongPress(SuraAyah suraAyah) {
    if (isVisible()) {
      final int page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah);
      if (page != lastLongPressPage) {
        ayahTrackerPresenter.endAyahMode(ayahSelectedListener);
      }
      lastLongPressPage = page;
      ayahTrackerPresenter.handleLongClick(suraAyah, ayahSelectedListener);
    }
  }

  @Override
  public void handleRetryClicked() {
    hidePageDownloadError();
    quranPagePresenter.get().downloadImages();
  }

  @Override
  public void onScrollChanged(int x, int y, int oldx, int oldy) {
    // no-op - no image ScrollView in this mode.
  }

  @Override
  public void endAyahMode() {
    if (isVisible()) {
      ayahTrackerPresenter.endAyahMode(ayahSelectedListener);
    }
  }

  @Override
  public void requestMenuPositionUpdate() {
    if (isVisible()) {
      ayahTrackerPresenter.requestMenuPositionUpdate(ayahSelectedListener);
    }
  }
}
