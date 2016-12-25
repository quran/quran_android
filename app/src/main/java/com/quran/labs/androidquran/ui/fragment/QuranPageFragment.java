package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.HighlightInfo;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.quran.CoordinatesModel;
import com.quran.labs.androidquran.presenter.quran.QuranPagePresenter;
import com.quran.labs.androidquran.presenter.quran.QuranPageScreen;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.helpers.PageDownloadListener;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
import com.quran.labs.androidquran.widgets.QuranImagePageLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import javax.inject.Inject;

import timber.log.Timber;

import static com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType;

public class QuranPageFragment extends Fragment
    implements AyahTracker, PageController, PageDownloadListener, QuranPageScreen {
  private static final String PAGE_NUMBER_EXTRA = "pageNumber";

  private int pageNumber;
  private Map<String, List<AyahBounds>> coordinatesData;

  private AyahSelectedListener ayahSelectedListener;

  private boolean overlayText;
  private Future<?> pageLoadTask;

  @Inject BookmarkModel bookmarkModel;
  @Inject QuranPageWorker quranPageWorker;
  @Inject CoordinatesModel coordinatesModel;

  private HighlightingImageView imageView;
  private QuranImagePageLayout quranPageLayout;
  private QuranPagePresenter quranPagePresenter;
  private HighlightInfo highlightInfo;

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

      if (coordinatesData != null && coordinatesData.isEmpty()) {
        // we tried to get coordinate data before but failed, and now something is asking
        // for an update, so let's try again.
        quranPagePresenter.refresh();
      }
    }
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
  }

  @Override
  public void onStop() {
    quranPagePresenter.unbind(this);
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
  public void onLoadImageResponse(BitmapDrawable drawable, Response response) {
    pageLoadTask = null;
    if (quranPageLayout == null || !isAdded()) {
      return;
    }

    if (drawable != null) {
      imageView.setImageDrawable(drawable);
    } else if (response != null) {
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
      quranPageLayout.setOnClickListener(v -> {
        if (ayahSelectedListener != null) {
          ayahSelectedListener.onClick(EventType.SINGLE_TAP);
        }
      });
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
    imageView.setPageBounds(pageCoordinates);
    if (overlayText) {
      Context context = getContext();
      String suraText = QuranInfo.getSuraNameFromPage(context, page, true);
      String juzText = QuranInfo.getJuzString(context, page);
      String pageText = QuranUtils.getLocalizedNumber(context, page);
      String rub3Text = QuranDisplayHelper.displayRub3(context,page);
      imageView.setOverlayText(suraText, juzText, pageText, rub3Text);
    }
  }

  @Override
  public void setBookmarksOnPage(List<Bookmark> bookmarks) {
    for (int i = 0, bookmarksSize = bookmarks.size(); i < bookmarksSize; i++) {
      Bookmark taggedAyah = bookmarks.get(i);
      imageView.highlightAyah(taggedAyah.sura,
          taggedAyah.ayah, HighlightType.BOOKMARK);
    }

    if (coordinatesData != null) {
      imageView.invalidate();
    }
  }

  @Override
  public void setAyahCoordinatesData(int page, Map<String, List<AyahBounds>> coordinates) {
    if (isAdded()) {
      coordinatesData = coordinates;
      if (!coordinates.isEmpty()) {
        imageView.setCoordinateData(coordinates);
        if (highlightInfo != null) {
          handleHighlightAyah(highlightInfo.sura, highlightInfo.ayah,
              highlightInfo.highlightType, highlightInfo.scrollToAyah);
          highlightInfo = null;
        } else {
          imageView.invalidate();
        }
      }
    }
  }

  @Override
  public void setAyahCoordinatesError() {
    coordinatesData = new HashMap<>();
  }

  @Override
  public void highlightAyat(int page, Set<String> ayahKeys, HighlightType type) {
    if (page == pageNumber && quranPageLayout != null) {
      imageView.highlightAyat(ayahKeys, type);
      imageView.invalidate();
    }
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    if (coordinatesData == null) {
      highlightInfo = new HighlightInfo(sura, ayah, type, scrollToAyah);
    } else {
      handleHighlightAyah(sura, ayah, type, scrollToAyah);
    }
  }

  private void handleHighlightAyah(int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    imageView.highlightAyah(sura, ayah, type);
    if (scrollToAyah && quranPageLayout.canScroll()) {
      final RectF highlightBounds = ImageAyahUtils.
          getYBoundsForHighlight(coordinatesData, sura, ayah);
      if (highlightBounds != null) {
        int screenHeight = QuranScreenInfo.getInstance().getHeight();

        Matrix matrix = imageView.getImageMatrix();
        matrix.mapRect(highlightBounds);

        int currentScrollY = quranPageLayout.getCurrentScrollY();
        final boolean topOnScreen = highlightBounds.top > currentScrollY &&
            highlightBounds.top < currentScrollY + screenHeight;
        final boolean bottomOnScreen = highlightBounds.bottom > currentScrollY &&
            highlightBounds.bottom < currentScrollY + screenHeight;

        if (!topOnScreen || !bottomOnScreen) {
          int y = (int) highlightBounds.top - (int) (0.05 * screenHeight);
          quranPageLayout.smoothScrollLayoutTo(y);
        }
      }
    }
    imageView.invalidate();
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type) {
    highlightAyah(sura, ayah, type, true);
  }

  @Override
  public AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
      int toolBarWidth, int toolBarHeight) {
    final List<AyahBounds> bounds = coordinatesData == null ? null :
        coordinatesData.get(sura + ":" + ayah);
    final int screenWidth = imageView == null ? 0 : imageView.getWidth();
    if (bounds != null && screenWidth > 0) {
      final int screenHeight = QuranScreenInfo.getInstance().getHeight();
      AyahToolBar.AyahToolBarPosition position =
          ImageAyahUtils.getToolBarPosition(bounds,
              imageView.getImageMatrix(), screenWidth, screenHeight,
              toolBarWidth, toolBarHeight);
      // If we're in landscape mode (wrapped in SV) update the y-offset
      position.yScroll = 0 - quranPageLayout.getCurrentScrollY();
      return position;
    }
    return null;
  }

  @Override
  public void unHighlightAyah(int sura, int ayah, HighlightType type) {
    imageView.unHighlight(sura, ayah, type);
  }

  @Override
  public void unHighlightAyahs(HighlightType type) {
    imageView.unHighlight(type);
  }

  private void handlePress(MotionEvent event, EventType eventType) {
    QuranAyah result = ImageAyahUtils.getAyahFromCoordinates(
        coordinatesData, imageView, event.getX(), event.getY());
    if (result != null && ayahSelectedListener != null) {
      SuraAyah suraAyah = new SuraAyah(result.getSura(), result.getAyah());
      ayahSelectedListener.onAyahSelected(eventType, suraAyah, this);
    }
  }

  @Override
  public void onScrollChanged(int x, int y, int oldx, int oldy) {
    PagerActivity activity = (PagerActivity) getActivity();
    if (activity != null) {
      activity.onQuranPageScroll(y);
    }
  }

  private void checkCoordinateData() {
    Activity activity = getActivity();
    if (activity instanceof PagerActivity &&
        (!QuranFileUtils.haveAyaPositionFile(activity) ||
            !QuranFileUtils.hasArabicSearchDatabase(activity))) {
      PagerActivity pagerActivity = (PagerActivity) activity;
      pagerActivity.showGetRequiredFilesDialog();
    }
  }

  @Override
  public void handleRetryClicked() {
    quranPageLayout.setOnClickListener(null);
    quranPageLayout.setClickable(false);
    downloadImage();
  }

  @Override
  public boolean handleTouchEvent(MotionEvent event,
      EventType eventType, int page) {
    if (eventType == EventType.DOUBLE_TAP) {
      unHighlightAyahs(HighlightType.SELECTION);
    }

    if (ayahSelectedListener == null) {
      return false;
    }

    if (ayahSelectedListener.isListeningForAyahSelection(eventType)) {
      if (coordinatesData != null) {
        if (coordinatesData.isEmpty()) {
          checkCoordinateData();
        } else {
          handlePress(event, eventType);
        }
      }
      return true;
    } else {
      return ayahSelectedListener.onClick(eventType);
    }
  }
}
