package com.quran.labs.androidquran.ui.fragment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.quran.CoordinatesModel;
import com.quran.labs.androidquran.presenter.quran.QuranPagePresenter;
import com.quran.labs.androidquran.presenter.quran.QuranPageScreen;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahImageTrackerItem;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerItem;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTranslationTrackerItem;
import com.quran.labs.androidquran.presenter.translation.TranslationPresenter;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.QuranPage;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
import com.quran.labs.androidquran.widgets.QuranImagePageLayout;
import com.quran.labs.androidquran.widgets.QuranTranslationPageLayout;
import com.quran.labs.androidquran.widgets.TabletView;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import timber.log.Timber;

import static com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType;

public class TabletFragment extends Fragment
    implements PageController, TranslationPresenter.TranslationScreen,
    QuranPage, QuranPageScreen, AyahTrackerPresenter.AyahInteractionHandler {
  private static final String FIRST_PAGE_EXTRA = "pageNumber";
  private static final String MODE_EXTRA = "mode";

  public static class Mode {
    public static final int ARABIC = 1;
    public static final int TRANSLATION = 2;
  }

  private int mode;
  private int pageNumber;
  private boolean ayahCoordinatesError;
  private AyahSelectedListener ayahSelectedListener;
  private TranslationView leftTranslation, rightTranslation = null;
  private HighlightingImageView leftImageView, rightImageView = null;
  private CompositeDisposable compositeDisposable = new CompositeDisposable();

  private TranslationPresenter leftPageTranslationPresenter;
  private TranslationPresenter rightPageTranslationPresenter;
  private QuranPagePresenter quranPagePresenter;
  private AyahTrackerPresenter ayahTrackerPresenter;

  private TabletView mainView;

  @Inject BookmarkModel bookmarkModel;
  @Inject QuranPageWorker quranPageWorker;
  @Inject CoordinatesModel coordinatesModel;

  public static TabletFragment newInstance(int firstPage, int mode) {
    final TabletFragment f = new TabletFragment();
    final Bundle args = new Bundle();
    args.putInt(FIRST_PAGE_EXTRA, firstPage);
    args.putInt(MODE_EXTRA, mode);
    f.setArguments(args);
    return f;
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final Context context = getActivity();
    mainView = new TabletView(context);

    mode = getArguments().getInt(MODE_EXTRA, Mode.ARABIC);
    if (mode == Mode.ARABIC) {
      mainView.init(TabletView.QURAN_PAGE, TabletView.QURAN_PAGE);
      leftImageView = ((QuranImagePageLayout) mainView.getLeftPage()).getImageView();
      rightImageView = ((QuranImagePageLayout) mainView.getRightPage()).getImageView();
      mainView.setPageController(this, pageNumber, pageNumber - 1);
    } else if (mode == Mode.TRANSLATION) {
      mainView.init(TabletView.TRANSLATION_PAGE, TabletView.TRANSLATION_PAGE);
      leftTranslation =
          ((QuranTranslationPageLayout) mainView.getLeftPage()).getTranslationView();
      rightTranslation =
          ((QuranTranslationPageLayout) mainView.getRightPage()).getTranslationView();

      leftTranslation.setTranslationClickedListener(
          () -> ((PagerActivity) getActivity()).toggleActionBar());
      rightTranslation.setTranslationClickedListener(
          () -> ((PagerActivity) getActivity()).toggleActionBar());
      mainView.setPageController(null, pageNumber, pageNumber - 1);
    }

    return mainView;
  }

  @Override
  public void onStart() {
    super.onStart();
    ayahTrackerPresenter.bind(this);
    if (mode == Mode.ARABIC) {
      quranPagePresenter.bind(this);
    } else {
      leftPageTranslationPresenter.bind(this);
      rightPageTranslationPresenter.bind(this);
    }
  }

  @Override
  public void onStop() {
    ayahTrackerPresenter.unbind(this);
    if (mode == Mode.ARABIC) {
      quranPagePresenter.unbind(this);
    } else {
      leftPageTranslationPresenter.unbind(this);
      rightPageTranslationPresenter.unbind(this);
    }
    super.onStop();
  }

  @Override
  public void onResume() {
    super.onResume();
    updateView();
    if (mode == Mode.TRANSLATION) {
      rightTranslation.refresh();
      leftTranslation.refresh();
    }
  }

  @Override
  public void updateView() {
    if (isAdded()) {
      final QuranSettings settings = QuranSettings.getInstance(getContext());
      final boolean useNewBackground = settings.useNewBackground();
      final boolean isNightMode = settings.isNightMode();
      mainView.updateView(isNightMode, useNewBackground);
    }
  }

  @Override
  public AyahTracker getAyahTracker() {
    return ayahTrackerPresenter;
  }

  @Override
  public AyahTrackerItem[] getAyahTrackerItems() {
    AyahTrackerItem left;
    AyahTrackerItem right;
    if (mode == Mode.ARABIC) {
      left = new AyahImageTrackerItem(pageNumber, false, leftImageView);
      right = new AyahImageTrackerItem(pageNumber - 1, true, rightImageView);
    } else if (mode == Mode.TRANSLATION) {
      left = new AyahTranslationTrackerItem(pageNumber, leftTranslation);
      right = new AyahTranslationTrackerItem(pageNumber - 1, rightTranslation);
    } else {
      return new AyahTrackerItem[0];
    }
    return new AyahTrackerItem[] { right, left };
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    ((QuranApplication) (context.getApplicationContext())).getApplicationComponent().inject(this);
    if (context instanceof AyahSelectedListener) {
      ayahSelectedListener = (AyahSelectedListener) context;
    }

    pageNumber = getArguments().getInt(FIRST_PAGE_EXTRA);
    mode = getArguments().getInt(MODE_EXTRA, Mode.ARABIC);
    if (mode == Mode.TRANSLATION) {
      leftPageTranslationPresenter = new TranslationPresenter(context, pageNumber);
      rightPageTranslationPresenter = new TranslationPresenter(context, pageNumber - 1);
    } else if (mode == Mode.ARABIC) {
      quranPagePresenter = new QuranPagePresenter(bookmarkModel, coordinatesModel,
          QuranSettings.getInstance(context), QuranScreenInfo.getOrMakeInstance(context),
          quranPageWorker, true, pageNumber - 1, pageNumber);
    }
    ayahTrackerPresenter = new AyahTrackerPresenter();
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
    ImageView imageView = page == pageNumber - 1 ? rightImageView : leftImageView;
    imageView.setImageDrawable(new BitmapDrawable(getResources(), pageBitmap));
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
      leftPageTranslationPresenter.refresh();
      rightPageTranslationPresenter.refresh();
    }
  }

  @Override
  public void setVerses(int page, @NonNull List<QuranAyah> verses) {
    if (page == pageNumber) {
      leftTranslation.setAyahs(verses);
    } else if (page == pageNumber - 1) {
      rightTranslation.setAyahs(verses);
    }
  }

  public void refresh() {
    if (mode == Mode.TRANSLATION) {
      leftPageTranslationPresenter.refresh();
      rightPageTranslationPresenter.refresh();
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
  }

  @Override
  public void setBookmarksOnPage(List<Bookmark> bookmarks) {
    ayahTrackerPresenter.setAyahBookmarks(bookmarks);
  }

  @Override
  public void setPageCoordinates(int page, RectF pageCoordinates) {
    ayahTrackerPresenter.setPageBounds(page, pageCoordinates);
  }

  @Override
  public void setAyahCoordinatesError() {
    ayahCoordinatesError = true;
  }

  @Override
  public void setAyahCoordinatesData(int page, Map<String, List<AyahBounds>> coordinates) {
    ayahTrackerPresenter.setAyahCoordinates(page, coordinates);
  }

  @Override
  public boolean handleTouchEvent(MotionEvent event, EventType eventType, int page) {
    return isVisible() && ayahTrackerPresenter.handleTouchEvent(getActivity(), event, eventType,
        page, ayahSelectedListener, ayahCoordinatesError);
  }

  @Override
  public void handleRetryClicked() {
    hidePageDownloadError();
    quranPagePresenter.downloadImages();
  }

  @Override
  public void onScrollChanged(int x, int y, int oldx, int oldy) {
    // no-op - no image ScrollView in this mode.
  }
}
