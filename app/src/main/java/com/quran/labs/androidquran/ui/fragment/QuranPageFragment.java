package com.quran.labs.androidquran.ui.fragment;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.quran.CoordinatesModel;
import com.quran.labs.androidquran.presenter.quran.QuranPagePresenter;
import com.quran.labs.androidquran.presenter.quran.QuranPageScreen;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahImageTrackerItem;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahScrollableImageTrackerItem;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerItem;
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.helpers.PageDownloadListener;
import com.quran.labs.androidquran.ui.helpers.QuranPage;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
import com.quran.labs.androidquran.widgets.QuranImagePageLayout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.inject.Inject;

import timber.log.Timber;

import static com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType;

public class QuranPageFragment extends Fragment
    implements PageController, PageDownloadListener, QuranPage, QuranPageScreen,
    AyahTrackerPresenter.AyahInteractionHandler {
  private static final String PAGE_NUMBER_EXTRA = "pageNumber";

  private int pageNumber;
  private AyahSelectedListener ayahSelectedListener;

  private boolean overlayText;
  private Future<?> pageLoadTask;

  @Inject BookmarkModel bookmarkModel;
  @Inject QuranPageWorker quranPageWorker;
  @Inject CoordinatesModel coordinatesModel;

  private HighlightingImageView imageView;
  private QuranImagePageLayout quranPageLayout;
  private QuranPagePresenter quranPagePresenter;
  private AyahTrackerPresenter ayahTrackerPresenter;
  private boolean ayahCoordinatesError;

  public static QuranPageFragment newInstance(int page) {
    final QuranPageFragment f = new QuranPageFragment();
    final Bundle args = new Bundle();
    args.putInt(PAGE_NUMBER_EXTRA, page);
    f.setArguments(args);
    return f;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    pageNumber = getArguments() != null ? getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;
    setHasOptionsMenu(true);
  }

  @Override
  public void onResume() {
    super.onResume();
    updateView();
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final Context context = getActivity();
    quranPageLayout = new QuranImagePageLayout(context);
    quranPageLayout.setPageController(this, pageNumber);
    imageView = quranPageLayout.getImageView();
    return quranPageLayout;
  }

  @Override
  public void updateView() {
    if (isAdded()) {
      final QuranSettings settings = QuranSettings.getInstance(getActivity());
      final boolean useNewBackground = settings.useNewBackground();
      final boolean isNightMode = settings.isNightMode();
      overlayText = settings.shouldOverlayPageInfo();
      quranPageLayout.updateView(isNightMode, useNewBackground, 1);
      if (!settings.highlightBookmarks()) {
        imageView.unHighlight(HighlightType.BOOKMARK);
      }
      quranPagePresenter.refresh();
    }
  }

  @Override
  public AyahTracker getAyahTracker() {
    return ayahTrackerPresenter;
  }

  @Override
  public AyahTrackerItem[] getAyahTrackerItems() {
    return new AyahTrackerItem[]{
        quranPageLayout.canScroll() ?
            new AyahScrollableImageTrackerItem(pageNumber, quranPageLayout, imageView,
                overlayText) :
            new AyahImageTrackerItem(pageNumber, overlayText, imageView) };
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    ((QuranApplication) context.getApplicationContext()).getApplicationComponent().inject(this);
    if (context instanceof AyahSelectedListener) {
      ayahSelectedListener = (AyahSelectedListener) context;
    }

    int page = getArguments().getInt(PAGE_NUMBER_EXTRA);
    quranPagePresenter = new QuranPagePresenter(bookmarkModel,
        coordinatesModel, QuranSettings.getInstance(context), false, page);
    ayahTrackerPresenter = new AyahTrackerPresenter();
  }

  @Override
  public void onDetach() {
    ayahSelectedListener = null;
    super.onDetach();
  }

  @Override
  public void onStart() {
    super.onStart();
    quranPagePresenter.bind(this);
    ayahTrackerPresenter.bind(this);
  }

  @Override
  public void onStop() {
    quranPagePresenter.unbind(this);
    ayahTrackerPresenter.unbind(this);
    super.onStop();
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    downloadImage();
  }

  private void downloadImage() {
    if (isAdded()) {
      pageLoadTask = quranPageWorker.loadPage(
          QuranScreenInfo.getInstance().getWidthParam(), pageNumber, QuranPageFragment.this);
    }
  }

  @Override
  public void onLoadImageResponse(@Nullable BitmapDrawable drawable, @NonNull Response response) {
    pageLoadTask = null;
    if (isAdded()) {
      if (drawable != null) {
        imageView.setImageDrawable(drawable);
      } else {
        // failed to get the image... let's notify the user
        final int errorCode = response.getErrorCode();
        final int errorRes;
        switch (errorCode) {
          case Response.ERROR_SD_CARD_NOT_FOUND:
            errorRes = R.string.sdcard_error;
            break;
          case Response.ERROR_DOWNLOADING_ERROR:
            errorRes = R.string.download_error_network;
            break;
          default:
            errorRes = R.string.download_error_general;
        }
        quranPageLayout.showError(errorRes);
        quranPageLayout.setOnClickListener(v -> ayahSelectedListener.onClick(EventType.SINGLE_TAP));
      }
    }
  }

  public void cleanup() {
    Timber.d("cleaning up page %d", pageNumber);
    if (pageLoadTask != null) {
      pageLoadTask.cancel(false);
    }

    if (quranPageLayout != null) {
      imageView.setImageDrawable(null);
      quranPageLayout = null;
    }
  }

  @Override
  public void setPageCoordinates(int page, RectF pageCoordinates) {
    ayahTrackerPresenter.setPageBounds(page, pageCoordinates);
  }

  @Override
  public void setBookmarksOnPage(List<Bookmark> bookmarks) {
    ayahTrackerPresenter.setAyahBookmarks(bookmarks);
  }

  @Override
  public void setAyahCoordinatesData(int page, Map<String, List<AyahBounds>> coordinates) {
    ayahTrackerPresenter.setAyahCoordinates(page, coordinates);
    ayahCoordinatesError = false;
  }

  @Override
  public void setAyahCoordinatesError() {
    ayahCoordinatesError = true;
  }

  @Override
  public void onScrollChanged(int x, int y, int oldx, int oldy) {
    PagerActivity activity = (PagerActivity) getActivity();
    if (activity != null) {
      activity.onQuranPageScroll(y);
    }
  }

  @Override
  public void handleRetryClicked() {
    quranPageLayout.setOnClickListener(null);
    quranPageLayout.setClickable(false);
    downloadImage();
  }

  @Override
  public boolean handleTouchEvent(MotionEvent event, EventType eventType, int page) {
    return isVisible() && ayahTrackerPresenter.handleTouchEvent(getActivity(), event, eventType,
        page, ayahSelectedListener, ayahCoordinatesError);
  }
}
